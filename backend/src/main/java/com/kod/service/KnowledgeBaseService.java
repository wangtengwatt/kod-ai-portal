package com.kod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.common.BizException;
import com.kod.config.KnowledgeBaseProperties;
import com.kod.dto.KnowledgeBaseChunkReadRequest;
import com.kod.dto.KnowledgeBaseCreateRequest;
import com.kod.dto.KnowledgeBaseUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {
    private static final int CHUNK_SIZE = 2_000;
    private static final int CHUNK_OVERLAP = 200;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseProperties properties;

    public List<Map<String, Object>> list(Long userId) {
        requireEnabled();
        return jdbc.query("SELECT * FROM kod_knowledge_base WHERE user_id=? ORDER BY created_at DESC", (rs, row) -> {
            Map<String, Object> value = new HashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("name", rs.getString("name"));
            value.put("embeddingModel", rs.getString("embedding_model"));
            value.put("rerankModel", rs.getString("rerank_model"));
            putIfNotNull(value, "visionModel", rs.getString("vision_model"));
            putIfNotNull(value, "providerMode", rs.getString("provider_mode"));
            String parser = rs.getString("document_parser_json");
            if (StringUtils.hasText(parser)) value.put("documentParser", parseJson(parser));
            value.put("createdAt", rs.getLong("created_at"));
            return value;
        }, userId);
    }

    public void create(Long userId, KnowledgeBaseCreateRequest request) {
        requireEnabled();
        transactions.executeWithoutResult(status -> {
            long now = System.currentTimeMillis();
            jdbc.update("INSERT INTO kod_knowledge_base(user_id,name,embedding_model,rerank_model,vision_model,provider_mode,document_parser_json,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    userId, request.name(), request.embeddingModel(), request.rerankModel(), request.visionModel(),
                    request.providerMode(), request.documentParser() == null ? null : toJson(request.documentParser()), now, now);
        });
    }

    public void update(Long userId, long id, KnowledgeBaseUpdateRequest request) {
        Map<String, Object> current = ownedKnowledgeBase(userId, id);
        String name = request.name() == null ? String.valueOf(current.get("name")) : request.name();
        String rerank = request.rerankModel() == null ? String.valueOf(current.get("rerank_model")) : request.rerankModel();
        Object vision = request.visionModel() == null ? current.get("vision_model") : request.visionModel();
        jdbc.update("UPDATE kod_knowledge_base SET name=?,rerank_model=?,vision_model=?,updated_at=? WHERE id=? AND user_id=?",
                name, rerank, vision, System.currentTimeMillis(), id, userId);
    }

    public void delete(Long userId, long id) {
        ownedKnowledgeBase(userId, id);
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM kod_knowledge_base_chunk WHERE kb_id=? AND user_id=?", id, userId);
            jdbc.update("DELETE FROM kod_knowledge_base_file WHERE kb_id=? AND user_id=?", id, userId);
            jdbc.update("DELETE FROM kod_knowledge_base WHERE id=? AND user_id=?", id, userId);
        });
    }

    public List<Map<String, Object>> listFiles(Long userId, long kbId, int offset, int limit) {
        ownedKnowledgeBase(userId, kbId);
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("SELECT * FROM kod_knowledge_base_file WHERE kb_id=? AND user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> fileResponse(rs.getLong("id"), rs.getLong("kb_id"), rs.getString("filename"),
                        rs.getString("mime_type"), rs.getLong("file_size"), rs.getInt("chunk_count"),
                        rs.getInt("total_chunks"), rs.getString("status"), rs.getString("error_message"),
                        rs.getLong("created_at"), rs.getString("parser_type")),
                kbId, userId, safeLimit, safeOffset);
    }

    public long countFiles(Long userId, long kbId) {
        ownedKnowledgeBase(userId, kbId);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM kod_knowledge_base_file WHERE kb_id=? AND user_id=?",
                Long.class, kbId, userId);
        return count == null ? 0 : count;
    }

    public void upload(Long userId, long kbId, MultipartFile file) {
        ownedKnowledgeBase(userId, kbId);
        if (file.isEmpty()) throw new BizException(400, "知识库文件为空");
        if (file.getSize() > properties.getMaxFileBytes()) throw new BizException(413, "知识库文件超过服务端限制");
        try {
            byte[] raw = file.getBytes();
            String filename = safeFilename(file.getOriginalFilename());
            String mime = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
            processNewFile(userId, kbId, filename, mime, raw);
        } catch (IOException error) {
            throw new BizException(400, "无法读取上传文件", error);
        }
    }

    public void retry(Long userId, long fileId) {
        Map<String, Object> row = ownedFile(userId, fileId, true);
        byte[] raw = (byte[]) row.get("raw_data");
        processExistingFile(userId, fileId, ((Number) row.get("kb_id")).longValue(), raw);
    }

    public void pause(Long userId, long fileId) {
        ownedFile(userId, fileId, false);
        jdbc.update("UPDATE kod_knowledge_base_file SET status='paused',updated_at=? WHERE id=? AND user_id=?",
                System.currentTimeMillis(), fileId, userId);
    }

    public void resume(Long userId, long fileId) {
        retry(userId, fileId);
    }

    public void deleteFile(Long userId, long fileId) {
        ownedFile(userId, fileId, false);
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM kod_knowledge_base_chunk WHERE file_id=? AND user_id=?", fileId, userId);
            jdbc.update("DELETE FROM kod_knowledge_base_file WHERE id=? AND user_id=?", fileId, userId);
        });
    }

    public List<Map<String, Object>> search(Long userId, long kbId, String query) {
        ownedKnowledgeBase(userId, kbId);
        if (!StringUtils.hasText(query)) return List.of();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        Set<String> terms = terms(normalized);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id,c.file_id,c.chunk_index,c.content,f.filename,f.mime_type
                FROM kod_knowledge_base_chunk c JOIN kod_knowledge_base_file f ON f.id=c.file_id
                WHERE c.user_id=? AND c.kb_id=? AND f.status='ready' LIMIT 10000
                """, userId, kbId);
        return rows.stream()
                .map(row -> scored(row, normalized, terms))
                .filter(row -> ((Number) row.get("score")).doubleValue() > 0)
                .sorted(Comparator.comparingDouble((Map<String, Object> row) -> ((Number) row.get("score")).doubleValue()).reversed())
                .limit(12)
                .toList();
    }

    public List<Map<String, Object>> fileMetas(Long userId, long kbId, List<Long> fileIds) {
        ownedKnowledgeBase(userId, kbId);
        if (fileIds == null || fileIds.isEmpty()) return List.of();
        if (fileIds.size() > 100) throw new BizException(400, "一次最多读取 100 个文件元数据");
        String placeholders = String.join(",", java.util.Collections.nCopies(fileIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(kbId);
        args.addAll(fileIds);
        return jdbc.query("SELECT id,kb_id,filename,mime_type,file_size,chunk_count,total_chunks,status,created_at FROM kod_knowledge_base_file WHERE user_id=? AND kb_id=? AND id IN (" + placeholders + ")",
                (rs, row) -> Map.of(
                        "id", rs.getLong("id"), "kbId", rs.getLong("kb_id"), "filename", rs.getString("filename"),
                        "mimeType", rs.getString("mime_type"), "fileSize", rs.getLong("file_size"),
                        "chunkCount", rs.getInt("chunk_count"), "totalChunks", rs.getInt("total_chunks"),
                        "status", rs.getString("status"), "createdAt", rs.getLong("created_at")), args.toArray());
    }

    public List<Map<String, Object>> readChunks(Long userId, long kbId, KnowledgeBaseChunkReadRequest request) {
        ownedKnowledgeBase(userId, kbId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeBaseChunkReadRequest.ChunkRef ref : request.chunks()) {
            result.addAll(jdbc.query("""
                    SELECT c.file_id,c.chunk_index,c.content,f.filename
                    FROM kod_knowledge_base_chunk c JOIN kod_knowledge_base_file f ON f.id=c.file_id
                    WHERE c.user_id=? AND c.kb_id=? AND c.file_id=? AND c.chunk_index=?
                    """, (rs, row) -> Map.of("fileId", rs.getLong("file_id"), "filename", rs.getString("filename"),
                    "chunkIndex", rs.getInt("chunk_index"), "text", rs.getString("content")),
                    userId, kbId, ref.fileId(), ref.chunkIndex()));
        }
        return result;
    }

    private void processNewFile(Long userId, long kbId, String filename, String mime, byte[] raw) {
        String text;
        try {
            text = extract(raw);
        } catch (Exception error) {
            text = null;
        }
        String extracted = text;
        transactions.executeWithoutResult(status -> {
            long now = System.currentTimeMillis();
            jdbc.update("INSERT INTO kod_knowledge_base_file(kb_id,user_id,filename,mime_type,file_size,raw_data,status,created_at,updated_at) VALUES(?,?,?,?,?,?,'processing',?,?)",
                    kbId, userId, filename, mime, raw.length, raw, now, now);
            Long fileId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            if (fileId == null) throw new IllegalStateException("无法创建知识库文件");
            persistExtracted(userId, kbId, fileId, extracted);
        });
    }

    private void processExistingFile(Long userId, long fileId, long kbId, byte[] raw) {
        String text;
        try {
            text = extract(raw);
        } catch (Exception error) {
            jdbc.update("UPDATE kod_knowledge_base_file SET status='failed',error_message=?,updated_at=? WHERE id=? AND user_id=?",
                    boundedError(error), System.currentTimeMillis(), fileId, userId);
            return;
        }
        String extracted = text;
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM kod_knowledge_base_chunk WHERE file_id=? AND user_id=?", fileId, userId);
            persistExtracted(userId, kbId, fileId, extracted);
        });
    }

    private void persistExtracted(Long userId, long kbId, long fileId, String text) {
        if (!StringUtils.hasText(text)) {
            jdbc.update("UPDATE kod_knowledge_base_file SET status='failed',error_message='文档没有可提取文本',updated_at=? WHERE id=? AND user_id=?",
                    System.currentTimeMillis(), fileId, userId);
            return;
        }
        List<String> chunks = chunk(text);
        long now = System.currentTimeMillis();
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("INSERT INTO kod_knowledge_base_chunk(kb_id,file_id,user_id,chunk_index,content,created_at) VALUES(?,?,?,?,?,?)",
                    kbId, fileId, userId, i, chunks.get(i), now);
        }
        jdbc.update("UPDATE kod_knowledge_base_file SET status='ready',chunk_count=?,total_chunks=?,error_message=NULL,parser_type='tika',updated_at=? WHERE id=? AND user_id=?",
                chunks.size(), chunks.size(), now, fileId, userId);
    }

    private String extract(byte[] raw) throws Exception {
        Tika tika = new Tika();
        String value = tika.parseToString(new ByteArrayInputStream(raw), new Metadata(), properties.getMaxExtractedCharacters());
        return value.replace("\u0000", "").trim();
    }

    private List<String> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            if (end < normalized.length()) {
                int boundary = normalized.lastIndexOf('\n', end);
                if (boundary > start + CHUNK_SIZE / 2) end = boundary;
            }
            String value = normalized.substring(start, end).trim();
            if (!value.isEmpty()) chunks.add(value);
            if (end >= normalized.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private Map<String, Object> scored(Map<String, Object> row, String phrase, Set<String> terms) {
        String text = String.valueOf(row.get("content"));
        String lower = text.toLowerCase(Locale.ROOT);
        double score = occurrences(lower, phrase) * 3.0;
        for (String term : terms) score += occurrences(lower, term);
        Map<String, Object> result = new HashMap<>();
        result.put("id", row.get("id"));
        result.put("score", score / Math.max(1, Math.sqrt(text.length())));
        result.put("text", text);
        result.put("fileId", row.get("file_id"));
        result.put("filename", row.get("filename"));
        result.put("mimeType", row.get("mime_type"));
        result.put("chunkIndex", row.get("chunk_index"));
        return result;
    }

    private Set<String> terms(String query) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : query.split("[^\\p{L}\\p{N}_]+")) if (value.length() >= 2) result.add(value);
        if (result.isEmpty() && query.length() >= 2) result.add(query);
        return result;
    }

    private int occurrences(String text, String value) {
        if (value.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0 && count < 100) {
            count++;
            index += Math.max(1, value.length());
        }
        return count;
    }

    private Map<String, Object> ownedKnowledgeBase(Long userId, long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM kod_knowledge_base WHERE id=? AND user_id=?", id, userId);
        if (rows.isEmpty()) throw new BizException(404, "知识库不存在");
        return rows.get(0);
    }

    private Map<String, Object> ownedFile(Long userId, long id, boolean includeRaw) {
        String columns = includeRaw ? "id,kb_id,raw_data" : "id,kb_id";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + columns + " FROM kod_knowledge_base_file WHERE id=? AND user_id=?", id, userId);
        if (rows.isEmpty()) throw new BizException(404, "知识库文件不存在");
        return rows.get(0);
    }

    private Map<String, Object> fileResponse(long id, long kbId, String filename, String mime, long size, int chunkCount,
                                              int totalChunks, String status, String error, long createdAt, String parserType) {
        Map<String, Object> value = new HashMap<>();
        value.put("id", id);
        value.put("kb_id", kbId);
        value.put("filename", filename);
        value.put("filepath", "cloud://knowledge-base/" + kbId + "/" + id);
        value.put("mime_type", mime);
        value.put("file_size", size);
        value.put("chunk_count", chunkCount);
        value.put("total_chunks", totalChunks);
        value.put("status", status);
        value.put("error", error == null ? "" : error);
        value.put("createdAt", createdAt);
        value.put("parsed_remotely", 1);
        if (parserType != null) value.put("parser_type", parserType);
        return value;
    }

    private String safeFilename(String value) {
        String filename = value == null ? "document" : value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        if (filename.isEmpty()) filename = "document";
        return filename.length() > 512 ? filename.substring(0, 512) : filename;
    }

    private String boundedError(Exception error) {
        String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new BizException(503, "云端知识库尚未启用");
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new BizException(400, "知识库配置 JSON 无效", error); }
    }

    private JsonNode parseJson(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException error) { return objectMapper.createObjectNode(); }
    }
}

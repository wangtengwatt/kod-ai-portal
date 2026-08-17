package com.kod.service;

import com.kod.common.BizException;
import com.kod.util.ComputeDeliveryCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 卡时批次、存入、销售、询价、受限转让、托管和 GPU 取出的统一领域服务。 */
@Service
@RequiredArgsConstructor
public class ComputeCardHourMarketService {

    private static final BigDecimal ZERO3 = new BigDecimal("0.000");
    private static final BigDecimal HALF_TRADE_FEE = new BigDecimal("0.001");
    private static final String STANDARD = "STANDARD";
    private static final String SPECIFIC = "SPECIFIC";
    private static final String PURPOSE_LISTING = "CARD_LISTING";
    private static final String PURPOSE_REDEMPTION = "CARD_REDEMPTION";

    private final JdbcTemplate jdbc;
    private final ComputeCenterService center;
    private final ComputeDeliveryCrypto deliveryCrypto;

    public List<Map<String, Object>> publicListings() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT l.id,l.listing_no AS listingNo,l.market_type AS marketType,l.asset_type AS assetType,
                       l.gpu_model AS gpuModel,l.quantity,l.unit_price AS unitPrice,
                       l.price_currency AS priceCurrency,l.asset_expires_at AS assetExpiresAt,
                       l.listing_expires_at AS listingExpiresAt,l.rate_version AS rateVersion,
                       l.rate_multiplier AS rateMultiplier,l.title,l.description,l.status,l.create_time AS createTime,
                       u.email AS sellerEmail,s.display_name AS sellerName,
                       CASE WHEN i.status='APPROVED' THEN 1 ELSE 0 END AS identityVerified,
                       CASE WHEN n.status='RUNNING' THEN 1 ELSE 0 END AS nodeVerified
                FROM compute_card_hour_listing l
                JOIN sys_user u ON u.id=l.seller_user_id
                LEFT JOIN compute_supplier s ON s.user_id=l.seller_user_id
                LEFT JOIN compute_identity_verification i ON i.id=(
                    SELECT MAX(iv.id) FROM compute_identity_verification iv
                    WHERE iv.user_id=l.seller_user_id AND iv.status='APPROVED'
                )
                LEFT JOIN compute_gpu_node n ON n.id=l.node_id
                WHERE l.status='PUBLISHED' AND (l.listing_expires_at IS NULL OR l.listing_expires_at>NOW())
                  AND l.asset_expires_at>DATE_ADD(NOW(),INTERVAL 7 DAY)
                ORDER BY l.id DESC LIMIT 300
                """);
        rows.forEach(this::maskSeller);
        return rows;
    }

    public Map<String, Object> marketStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("standardInventory", scalar("""
                SELECT COALESCE(SUM(quantity),0) FROM compute_card_hour_listing
                WHERE status='PUBLISHED' AND asset_type='STANDARD'
                  AND (listing_expires_at IS NULL OR listing_expires_at>NOW()) AND asset_expires_at>NOW()
                """));
        result.put("specificInventory", scalar("""
                SELECT COALESCE(SUM(quantity),0) FROM compute_card_hour_listing
                WHERE status='PUBLISHED' AND asset_type='SPECIFIC'
                  AND (listing_expires_at IS NULL OR listing_expires_at>NOW()) AND asset_expires_at>NOW()
                """));
        result.put("volume24h", scalar("""
                SELECT COALESCE(SUM(quantity),0) FROM compute_card_hour_trade
                WHERE status='COMPLETED' AND completed_at>=DATE_SUB(NOW(),INTERVAL 24 HOUR)
                """));
        result.put("recentTrades", jdbc.queryForList("""
                SELECT t.trade_no AS tradeNo,t.asset_type AS assetType,t.gpu_model AS gpuModel,
                       t.quantity,t.unit_price AS unitPrice,t.price_currency AS priceCurrency,
                       t.completed_at AS completedAt,bu.email AS buyerEmail,su.email AS sellerEmail
                FROM compute_card_hour_trade t
                JOIN sys_user bu ON bu.id=t.buyer_user_id JOIN sys_user su ON su.id=t.seller_user_id
                WHERE t.status='COMPLETED' ORDER BY t.id DESC LIMIT 20
                """).stream().peek(row -> {
                    row.put("buyerEmail", maskEmail(Objects.toString(row.get("buyerEmail"), "")));
                    row.put("sellerEmail", maskEmail(Objects.toString(row.get("sellerEmail"), "")));
                }).toList());
        return result;
    }

    public List<Map<String, Object>> lots(long userId) {
        return jdbc.queryForList("""
                SELECT l.id,l.asset_type AS assetType,l.gpu_model AS gpuModel,l.issuer_user_id AS issuerUserId,
                       l.node_id AS nodeId,l.source_type AS sourceType,l.source_ref AS sourceRef,
                       l.original_amount AS originalAmount,l.remaining_amount AS remainingAmount,
                       l.frozen_amount AS frozenAmount,(l.remaining_amount-l.frozen_amount) AS availableAmount,
                       l.rate_version AS rateVersion,l.rate_multiplier AS rateMultiplier,
                       l.custody_status AS custodyStatus,l.custody_fee_accrued AS custodyFeeAccrued,
                       l.expires_at AS expiresAt,l.create_time AS createTime,n.node_name AS nodeName
                FROM compute_card_hour_lot l LEFT JOIN compute_gpu_node n ON n.id=l.node_id
                WHERE l.owner_user_id=? AND l.remaining_amount>0 ORDER BY
                  CASE WHEN l.expires_at IS NULL THEN 1 ELSE 0 END,l.expires_at,l.id
                """, userId);
    }

    public Map<String, Object> custody(long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("lots", lots(userId));
        result.put("feeEnabled", false);
        result.put("accruedFee", scalar("""
                SELECT COALESCE(SUM(custody_fee_accrued),0) FROM compute_card_hour_lot
                WHERE owner_user_id=? AND remaining_amount>0
                """, userId));
        result.put("rule", "试运行阶段仅记录托管批次、有效期和冻结状态，不实际扣除托管费");
        return result;
    }

    public List<Map<String, Object>> rates() {
        return jdbc.queryForList("""
                SELECT id,version_no AS versionNo,gpu_model AS gpuModel,multiplier,status,
                       effective_from AS effectiveFrom,notes,create_time AS createTime
                FROM compute_card_hour_rate_rule ORDER BY effective_from DESC,id DESC
                """);
    }

    @Transactional
    public Map<String, Object> createListing(long userId, ListingInput input) {
        String marketType = upper(input.marketType());
        if (!List.of("PRIMARY_SALE", "IDLE_TRANSFER").contains(marketType)) {
            throw new BizException(400, "挂单类型必须是卡时销售或闲置卡时转让");
        }
        if ("PRIMARY_SALE".equals(marketType)) requireApprovedSupplier(userId);
        BigDecimal quantity = positive3(input.quantity(), "出售数量");
        BigDecimal unitPrice = positive4(input.unitPrice(), "单价");
        LocalDateTime assetExpiry = requireMarketExpiry(input.assetExpiresAt());
        LocalDateTime listingExpiry=input.listingExpiresAt()==null?assetExpiry:input.listingExpiresAt();
        if (!listingExpiry.isAfter(LocalDateTime.now()) || listingExpiry.isAfter(assetExpiry)) {
            throw new BizException(400,"销售截止时间必须晚于当前时间且不能超过卡时批次有效期");
        }
        Map<String, Object> lot = lockLot(userId, input.sourceLotId());
        String sourceAssetType = Objects.toString(lot.get("assetType"), STANDARD);
        String requestedAssetType = upper(input.assetType());
        if (!List.of(STANDARD, SPECIFIC).contains(requestedAssetType)) {
            throw new BizException(400, "卡时类型无效");
        }
        if ("IDLE_TRANSFER".equals(marketType) && !sourceAssetType.equals(requestedAssetType)) {
            throw new BizException(400, "闲置转让必须保持原批次的卡时类型");
        }
        if (input.assetExpiresAt().isAfter(asDateTime(lot.get("expiresAt"), LocalDateTime.MAX))) {
            throw new BizException(400, "商品有效期不能超过来源批次有效期");
        }

        String gpuModel = null;
        Long nodeId = null;
        String rateVersion = null;
        BigDecimal multiplier = null;
        BigDecimal collateral;
        if (SPECIFIC.equals(requestedAssetType)) {
            gpuModel = clean(input.gpuModel(), 128, "GPU 型号");
            if ("PRIMARY_SALE".equals(marketType)) {
                if (!STANDARD.equals(sourceAssetType) || !"GPU_DEPOSIT".equals(lot.get("sourceType"))) {
                    throw new BizException(400, "指定 GPU 卡时只能从验收入账的标准卡时批次生成");
                }
                nodeId = longOrNull(lot.get("nodeId"));
                if (nodeId == null || !gpuModel.equalsIgnoreCase(Objects.toString(lot.get("gpuModel"), ""))) {
                    throw new BizException(400, "来源批次与指定 GPU 型号或节点不匹配");
                }
                Map<String, Object> rate = activeRate(gpuModel);
                rateVersion = Objects.toString(rate.get("versionNo"));
                multiplier = decimal(rate.get("multiplier"), 4);
                collateral = scale3(quantity.multiply(multiplier));
            } else {
                gpuModel = Objects.toString(lot.get("gpuModel"));
                nodeId = longOrNull(lot.get("nodeId"));
                rateVersion = Objects.toString(lot.get("rateVersion"), null);
                multiplier = decimal(lot.get("rateMultiplier"), 4);
                collateral = quantity;
            }
        } else {
            if (!STANDARD.equals(sourceAssetType)) throw new BizException(400, "标准卡时商品必须选择标准卡时批次");
            collateral = quantity;
        }
        if (available(lot).compareTo(collateral) < 0) throw new BizException(400, "来源批次可用卡时不足");

        String listingNo = no("CL");
        String priceCurrency = STANDARD.equals(requestedAssetType) && "PRIMARY_SALE".equals(marketType)
                ? "CNY" : "CARD_HOUR";
        jdbc.update("""
                INSERT INTO compute_card_hour_listing(listing_no,seller_user_id,market_type,asset_type,gpu_model,
                    node_id,source_lot_id,quantity,unit_price,price_currency,collateral_card_hours,
                    asset_expires_at,listing_expires_at,rate_version,rate_multiplier,title,description,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED')
                """, listingNo,userId,marketType,requestedAssetType,gpuModel,nodeId,input.sourceLotId(),quantity,
                unitPrice,priceCurrency,collateral,ts(assetExpiry),ts(listingExpiry),rateVersion,multiplier,
                clean(input.title(),256,"商品标题"),safe(input.description(),1000));
        long listingId = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id FROM compute_card_hour_listing WHERE listing_no=?", Long.class, listingNo));
        freezeExactLot(userId,input.sourceLotId(),collateral,PURPOSE_LISTING,listingId,assetExpiry,"卡时商品上架冻结");
        center.notifyUser(userId,"CARD_LISTING_CREATED","卡时商品已上架",
                listingNo+" 已冻结来源批次，成交或下架前不可重复使用","CARD_LISTING",listingNo);
        return listing(listingId);
    }

    public List<Map<String, Object>> myListings(long userId) {
        return jdbc.queryForList("""
                SELECT id,listing_no AS listingNo,market_type AS marketType,asset_type AS assetType,gpu_model AS gpuModel,
                       quantity,unit_price AS unitPrice,price_currency AS priceCurrency,
                       asset_expires_at AS assetExpiresAt,listing_expires_at AS listingExpiresAt,
                       title,description,status,create_time AS createTime
                FROM compute_card_hour_listing WHERE seller_user_id=? ORDER BY id DESC LIMIT 300
                """, userId);
    }

    @Transactional
    public Map<String, Object> cancelListing(long userId, long listingId) {
        Map<String, Object> listing = lockListing(listingId);
        if (longValue(listing.get("sellerUserId")) != userId) throw new BizException(403, "只能下架自己的商品");
        if (!List.of("PUBLISHED", "QUOTE_RESERVED").contains(Objects.toString(listing.get("status")))) {
            throw new BizException(400, "商品当前不能下架");
        }
        jdbc.update("UPDATE compute_card_hour_listing SET status='CANCELLED' WHERE id=?", listingId);
        releasePurpose(userId,PURPOSE_LISTING,listingId,"卡时商品下架解冻");
        return listing(listingId);
    }

    @Transactional
    public Map<String, Object> createPurchaseQuote(long buyerUserId, long listingId) {
        Map<String, Object> listing = lockListing(listingId);
        if (!"PUBLISHED".equals(listing.get("status"))) throw new BizException(400, "商品已售出或已下架");
        if (longValue(listing.get("sellerUserId")) == buyerUserId) throw new BizException(400, "不能购买自己发布的商品");
        return createPurchaseQuoteInternal(buyerUserId, listing);
    }

    private Map<String, Object> createPurchaseQuoteInternal(long buyerUserId, Map<String, Object> listing) {
        String quoteNo = no("CQ");
        BigDecimal quantity = decimal(listing.get("quantity"),3);
        BigDecimal unitPrice = decimal(listing.get("unitPrice"),4);
        BigDecimal total = unitPrice.multiply(quantity).setScale(4,RoundingMode.HALF_UP);
        BigDecimal cnyRate = setting("card_hour_cny_rate",new BigDecimal("1.0020"));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        jdbc.update("""
                INSERT INTO compute_card_hour_purchase_quote(quote_no,listing_id,buyer_user_id,quantity,
                    unit_price_snapshot,price_currency,total_price,cny_rate_snapshot,buyer_fee_card_hours,
                    seller_fee_card_hours,asset_expires_at,status,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'LOCKED',?)
                """,quoteNo,listing.get("id"),buyerUserId,quantity,unitPrice,listing.get("priceCurrency"),total,
                cnyRate,HALF_TRADE_FEE,HALF_TRADE_FEE,listing.get("assetExpiresAt"),ts(expiresAt));
        return quoteByNo(quoteNo);
    }

    @Transactional
    public Map<String, Object> confirmPurchaseQuote(long buyerUserId,long quoteId,boolean autoTopUp) {
        Map<String,Object> quote = jdbc.queryForMap("""
                SELECT id,quote_no AS quoteNo,listing_id AS listingId,buyer_user_id AS buyerUserId,quantity,
                       unit_price_snapshot AS unitPrice,price_currency AS priceCurrency,total_price AS totalPrice,
                       cny_rate_snapshot AS cnyRate,buyer_fee_card_hours AS buyerFee,
                       seller_fee_card_hours AS sellerFee,asset_expires_at AS assetExpiresAt,status,expires_at AS expiresAt
                FROM compute_card_hour_purchase_quote WHERE id=? FOR UPDATE
                """,quoteId);
        if (longValue(quote.get("buyerUserId"))!=buyerUserId) throw new BizException(403,"报价不属于当前用户");
        if (!"LOCKED".equals(quote.get("status")) || asDateTime(quote.get("expiresAt"),LocalDateTime.MIN).isBefore(LocalDateTime.now())) {
            throw new BizException(400,"锁价已过期，请重新询价");
        }
        Map<String,Object> listing=lockListing(longValue(quote.get("listingId")));
        if (!List.of("PUBLISHED","QUOTE_RESERVED").contains(Objects.toString(listing.get("status")))) {
            throw new BizException(400,"商品已售出或已下架");
        }
        BigDecimal total=decimal(quote.get("totalPrice"),4);
        BigDecimal buyerFee=decimal(quote.get("buyerFee"),3);
        if ("CNY".equals(quote.get("priceCurrency"))) {
            BigDecimal cost=total.add(buyerFee.multiply(decimal(quote.get("cnyRate"),4))).setScale(4,RoundingMode.HALF_UP);
            Map<String,Object> user=jdbc.queryForMap("SELECT id,balance FROM sys_user WHERE id=? FOR UPDATE",buyerUserId);
            if (decimal(user.get("balance"),4).compareTo(cost)<0) {
                throw new BizException(400,"人民币钱包余额不足，还差 ¥"+cost.subtract(decimal(user.get("balance"),4)).toPlainString());
            }
            jdbc.update("UPDATE sys_user SET balance=balance-? WHERE id=?",cost,buyerUserId);
        } else {
            center.ensureCardHoursForUserAction(buyerUserId,total.add(buyerFee),autoTopUp,null);
            center.consumeAvailable(buyerUserId,total.add(buyerFee),false,"CARD_MARKET_PURCHASE",
                    "CARD_QUOTE",Objects.toString(quote.get("quoteNo")),"购买卡时商品及买方服务费",buyerUserId,
                    "card-market-pay:"+quote.get("quoteNo"));
        }
        long sellerUserId=longValue(listing.get("sellerUserId"));
        long buyerLotId=deliverListedAsset(listing,buyerUserId);
        BigDecimal sellerGross="CNY".equals(quote.get("priceCurrency"))
                ? total.divide(decimal(quote.get("cnyRate"),4),3,RoundingMode.DOWN) : total.setScale(3,RoundingMode.DOWN);
        BigDecimal sellerIncome=sellerGross.subtract(decimal(quote.get("sellerFee"),3));
        if (sellerIncome.compareTo(ZERO3)>0) center.credit(sellerUserId,sellerIncome,"CARD_HOUR_SALES_INCOME",
                Objects.toString(quote.get("quoteNo")),null,"卡时商品成交收入",sellerUserId,
                "card-market-income:"+quote.get("quoteNo"));
        String tradeNo=no("CT");
        jdbc.update("""
                INSERT INTO compute_card_hour_trade(trade_no,listing_id,purchase_quote_id,buyer_user_id,seller_user_id,
                    market_type,asset_type,gpu_model,quantity,unit_price,price_currency,total_price,
                    buyer_fee_card_hours,seller_fee_card_hours,cny_amount,buyer_lot_id,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'COMPLETED')
                """,tradeNo,listing.get("id"),quoteId,buyerUserId,sellerUserId,listing.get("marketType"),
                listing.get("assetType"),listing.get("gpuModel"),quote.get("quantity"),quote.get("unitPrice"),
                quote.get("priceCurrency"),total,buyerFee,quote.get("sellerFee"),
                "CNY".equals(quote.get("priceCurrency"))?total:BigDecimal.ZERO,buyerLotId);
        jdbc.update("UPDATE compute_card_hour_listing SET status='SOLD' WHERE id=?",listing.get("id"));
        jdbc.update("UPDATE compute_card_hour_purchase_quote SET status='CONFIRMED',confirmed_at=NOW() WHERE id=?",quoteId);
        recordFees(tradeNo,buyerUserId,sellerUserId);
        center.notifyUser(buyerUserId,"CARD_TRADE_COMPLETED","卡时已购入",tradeNo+" 已完成，批次已进入我的资产","CARD_TRADE",tradeNo);
        center.notifyUser(sellerUserId,"CARD_TRADE_COMPLETED","卡时商品已成交",tradeNo+" 收入 "+sellerIncome+" 标准卡时","CARD_TRADE",tradeNo);
        return trade(tradeNo);
    }

    public List<Map<String,Object>> trades(long userId) {
        return jdbc.queryForList("""
                SELECT t.id,t.trade_no AS tradeNo,t.market_type AS marketType,t.asset_type AS assetType,
                       t.gpu_model AS gpuModel,t.quantity,t.unit_price AS unitPrice,t.price_currency AS priceCurrency,
                       t.total_price AS totalPrice,t.buyer_fee_card_hours AS buyerFee,
                       t.seller_fee_card_hours AS sellerFee,t.status,t.completed_at AS completedAt,
                       bu.email AS buyerEmail,su.email AS sellerEmail
                FROM compute_card_hour_trade t JOIN sys_user bu ON bu.id=t.buyer_user_id
                JOIN sys_user su ON su.id=t.seller_user_id
                WHERE t.buyer_user_id=? OR t.seller_user_id=? ORDER BY t.id DESC LIMIT 300
                """,userId,userId).stream().peek(row->{
                    row.put("buyerEmail",maskEmail(Objects.toString(row.get("buyerEmail"),"")));
                    row.put("sellerEmail",maskEmail(Objects.toString(row.get("sellerEmail"),"")));
                }).toList();
    }

    @Transactional
    public Map<String,Object> createRfq(long buyerUserId,RfqInput input) {
        String type=upper(input.assetType());
        if (!List.of(STANDARD,SPECIFIC).contains(type)) throw new BizException(400,"卡时类型无效");
        String gpu=SPECIFIC.equals(type)?clean(input.gpuModel(),128,"GPU 型号"):null;
        BigDecimal quantity=positive3(input.quantity(),"询价数量");
        LocalDateTime minimumExpiry=requireMarketExpiry(input.minimumExpiresAt());
        LocalDateTime closes=input.closesAt()==null?LocalDateTime.now().plusDays(3):input.closesAt();
        if (!closes.isAfter(LocalDateTime.now())) throw new BizException(400,"询价截止时间必须晚于当前时间");
        String no=no("RF");
        jdbc.update("""
                INSERT INTO compute_card_hour_rfq(rfq_no,buyer_user_id,asset_type,gpu_model,quantity,
                    minimum_expires_at,requirements,status,closes_at)
                VALUES (?,?,?,?,?,?,?,'OPEN',?)
                """,no,buyerUserId,type,gpu,quantity,ts(minimumExpiry),safe(input.requirements(),1000),ts(closes));
        notifyApprovedSuppliers("CARD_RFQ_CREATED","收到新的卡时询价",no+" 等待供应方报价","CARD_RFQ",no);
        return rfqByNo(no);
    }

    public List<Map<String,Object>> rfqs(long userId) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT r.id,r.rfq_no AS rfqNo,r.buyer_user_id AS buyerUserId,r.asset_type AS assetType,
                       r.gpu_model AS gpuModel,r.quantity,r.minimum_expires_at AS minimumExpiresAt,
                       r.requirements,r.status,r.selected_quote_id AS selectedQuoteId,r.closes_at AS closesAt,
                       r.create_time AS createTime,u.email AS buyerEmail,
                       (SELECT COUNT(*) FROM compute_card_hour_rfq_quote q WHERE q.rfq_id=r.id AND q.status='ACTIVE' AND q.expires_at>NOW()) AS quoteCount
                FROM compute_card_hour_rfq r JOIN sys_user u ON u.id=r.buyer_user_id
                WHERE r.status='OPEN' OR r.buyer_user_id=? ORDER BY r.id DESC LIMIT 300
                """,userId);
        rows.forEach(row->row.put("buyerEmail",maskEmail(Objects.toString(row.get("buyerEmail"),""))));
        return rows;
    }

    @Transactional
    public Map<String,Object> quoteRfq(long supplierUserId,long rfqId,RfqQuoteInput input) {
        requireApprovedSupplier(supplierUserId);
        Map<String,Object> rfq=jdbc.queryForMap("""
                SELECT id,buyer_user_id AS buyerUserId,asset_type AS assetType,gpu_model AS gpuModel,quantity,
                       minimum_expires_at AS minimumExpiresAt,status,closes_at AS closesAt
                FROM compute_card_hour_rfq WHERE id=? FOR UPDATE
                """,rfqId);
        if (longValue(rfq.get("buyerUserId"))==supplierUserId) throw new BizException(400,"不能给自己的询价报价");
        if (!"OPEN".equals(rfq.get("status")) || asDateTime(rfq.get("closesAt"),LocalDateTime.MIN).isBefore(LocalDateTime.now())) {
            throw new BizException(400,"询价已经关闭");
        }
        ListingInput listingInput=new ListingInput("PRIMARY_SALE",Objects.toString(rfq.get("assetType")),
                Objects.toString(rfq.get("gpuModel"),null),input.sourceLotId(),decimal(rfq.get("quantity"),3),
                positive4(input.unitPrice(),"报价单价"),asDateTime(rfq.get("minimumExpiresAt"),LocalDateTime.MIN),
                LocalDateTime.now().plusMinutes(30),"询价保留库存", "仅供询价 "+rfqId);
        Map<String,Object> listing=createListing(supplierUserId,listingInput);
        jdbc.update("UPDATE compute_card_hour_listing SET market_type='RFQ',status='QUOTE_RESERVED' WHERE id=?",listing.get("id"));
        String quoteNo=no("RQ");
        LocalDateTime expires=LocalDateTime.now().plusMinutes(30);
        jdbc.update("""
                INSERT INTO compute_card_hour_rfq_quote(quote_no,rfq_id,supplier_user_id,listing_id,unit_price,
                    price_currency,asset_expires_at,status,expires_at)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?)
                """,quoteNo,rfqId,supplierUserId,listing.get("id"),input.unitPrice(),listing.get("priceCurrency"),
                rfq.get("minimumExpiresAt"),ts(expires));
        center.notifyUser(longValue(rfq.get("buyerUserId")),"CARD_RFQ_QUOTED","询价收到新报价",
                quoteNo+" 有效期 30 分钟，请在到期前选择","CARD_RFQ",Long.toString(rfqId));
        return rfqQuote(quoteNo);
    }

    public List<Map<String,Object>> rfqQuotes(long userId,long rfqId) {
        Long owner=jdbc.queryForObject("SELECT buyer_user_id FROM compute_card_hour_rfq WHERE id=?",Long.class,rfqId);
        boolean supplier=center.isAdmin(userId) || (owner!=null && owner==userId);
        if (!supplier) throw new BizException(403,"只有询价买方或管理员可以查看全部报价");
        return jdbc.queryForList("""
                SELECT q.id,q.quote_no AS quoteNo,q.rfq_id AS rfqId,q.supplier_user_id AS supplierUserId,
                       q.listing_id AS listingId,q.unit_price AS unitPrice,q.price_currency AS priceCurrency,
                       q.asset_expires_at AS assetExpiresAt,q.status,q.expires_at AS expiresAt,
                       u.email AS supplierEmail,s.display_name AS supplierName
                FROM compute_card_hour_rfq_quote q JOIN sys_user u ON u.id=q.supplier_user_id
                LEFT JOIN compute_supplier s ON s.user_id=q.supplier_user_id
                WHERE q.rfq_id=? ORDER BY q.unit_price,q.id
                """,rfqId).stream().peek(row->{
                    row.put("supplierEmail",maskEmail(Objects.toString(row.get("supplierEmail"),"")));
                    row.put("supplierName",maskName(Objects.toString(row.get("supplierName"),"")));
                }).toList();
    }

    @Transactional
    public Map<String,Object> acceptRfqQuote(long buyerUserId,long rfqQuoteId,boolean autoTopUp) {
        Map<String,Object> rq=jdbc.queryForMap("""
                SELECT q.id,q.quote_no AS quoteNo,q.rfq_id AS rfqId,q.listing_id AS listingId,q.status,q.expires_at AS expiresAt,
                       r.buyer_user_id AS buyerUserId,r.status AS rfqStatus
                FROM compute_card_hour_rfq_quote q JOIN compute_card_hour_rfq r ON r.id=q.rfq_id
                WHERE q.id=? FOR UPDATE
                """,rfqQuoteId);
        if (longValue(rq.get("buyerUserId"))!=buyerUserId) throw new BizException(403,"只有询价买方可以选择报价");
        if (!"OPEN".equals(rq.get("rfqStatus")) || !"ACTIVE".equals(rq.get("status"))
                || asDateTime(rq.get("expiresAt"),LocalDateTime.MIN).isBefore(LocalDateTime.now())) {
            throw new BizException(400,"报价已经失效");
        }
        Map<String,Object> listing=lockListing(longValue(rq.get("listingId")));
        Map<String,Object> purchaseQuote=createPurchaseQuoteInternal(buyerUserId,listing);
        Map<String,Object> trade=confirmPurchaseQuote(buyerUserId,longValue(purchaseQuote.get("id")),autoTopUp);
        jdbc.update("UPDATE compute_card_hour_rfq SET status='COMPLETED',selected_quote_id=? WHERE id=?",rfqQuoteId,rq.get("rfqId"));
        jdbc.update("UPDATE compute_card_hour_rfq_quote SET status=CASE WHEN id=? THEN 'ACCEPTED' ELSE 'REJECTED' END WHERE rfq_id=?",
                rfqQuoteId,rq.get("rfqId"));
        releaseOtherRfqListings(longValue(rq.get("rfqId")),longValue(rq.get("listingId")));
        return trade;
    }

    @Transactional
    public Map<String,Object> createDeposit(long userId,DepositInput input) {
        requireApprovedSupplier(userId);
        Map<String,Object> node=jdbc.queryForMap("""
                SELECT id,supplier_user_id AS supplierUserId,gpu_model AS gpuModel,gpu_count AS gpuCount,status
                FROM compute_gpu_node WHERE id=? FOR UPDATE
                """,input.nodeId());
        if (longValue(node.get("supplierUserId"))!=userId || !"RUNNING".equals(node.get("status"))) {
            throw new BizException(400,"只能存入本人已验机且运行中的 GPU 节点额度");
        }
        validateSlot(input.availableFrom(),input.availableTo());
        if (input.availableTo().isBefore(LocalDateTime.now())) throw new BizException(400,"不能登记已经结束的 GPU 算力时段");
        LocalDateTime expiry=requireMarketExpiry(input.expiresAt());
        if (expiry.isAfter(input.availableTo())) throw new BizException(400,"批次有效期不能晚于本次承诺的算力结束时间");
        Long overlap=jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_card_hour_deposit
                WHERE node_id=? AND status IN ('PENDING','APPROVED') AND available_from<? AND available_to>?
                """,Long.class,input.nodeId(),ts(input.availableTo()),ts(input.availableFrom()));
        Long reservationOverlap=jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_reservation r JOIN compute_product p ON p.id=r.product_id
                WHERE p.node_id=? AND r.status NOT IN ('CANCELLED','REFUNDED','COMPLETED')
                  AND r.start_time<? AND r.end_time>?
                """,Long.class,input.nodeId(),ts(input.availableTo()),ts(input.availableFrom()));
        if ((overlap!=null&&overlap>0)||(reservationOverlap!=null&&reservationOverlap>0)) {
            throw new BizException(409,"该 GPU 时段已被存入申请或租赁订单占用，不能重复登记");
        }
        Map<String,Object> rate=activeRate(Objects.toString(node.get("gpuModel")));
        BigDecimal hours=BigDecimal.valueOf(Duration.between(input.availableFrom(),input.availableTo()).toMinutes())
                .divide(new BigDecimal("60"),3,RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(longValue(node.get("gpuCount")))).setScale(3,RoundingMode.DOWN);
        BigDecimal multiplier=decimal(rate.get("multiplier"),4);
        BigDecimal standard=scale3(hours.multiply(multiplier));
        String depositNo=no("DP");
        jdbc.update("""
                INSERT INTO compute_card_hour_deposit(deposit_no,supplier_user_id,node_id,gpu_model,gpu_count,
                    available_from,available_to,expires_at,gpu_hours,rate_version,rate_multiplier,standard_card_hours,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PENDING')
                """,depositNo,userId,input.nodeId(),node.get("gpuModel"),node.get("gpuCount"),ts(input.availableFrom()),
                ts(input.availableTo()),ts(expiry),hours,rate.get("versionNo"),multiplier,standard);
        notifyAdmins("CARD_DEPOSIT_PENDING","新的卡时存入申请",depositNo+" 等待验收","CARD_DEPOSIT",depositNo);
        return deposit(depositNo);
    }

    public List<Map<String,Object>> deposits(long userId,boolean admin) {
        if (admin) center.requireAdmin(userId);
        String where=admin?"1=1":"d.supplier_user_id=?";
        Object[] args=admin?new Object[]{}:new Object[]{userId};
        return jdbc.queryForList("""
                SELECT d.id,d.deposit_no AS depositNo,d.supplier_user_id AS supplierUserId,d.node_id AS nodeId,
                       d.gpu_model AS gpuModel,d.gpu_count AS gpuCount,d.available_from AS availableFrom,
                       d.available_to AS availableTo,d.expires_at AS expiresAt,d.gpu_hours AS gpuHours,
                       d.rate_version AS rateVersion,d.rate_multiplier AS rateMultiplier,
                       d.standard_card_hours AS standardCardHours,d.status,d.lot_id AS lotId,
                       d.rejection_reason AS rejectionReason,d.reviewed_at AS reviewedAt,d.create_time AS createTime,
                       u.email,n.node_name AS nodeName
                FROM compute_card_hour_deposit d JOIN sys_user u ON u.id=d.supplier_user_id
                JOIN compute_gpu_node n ON n.id=d.node_id WHERE 
                """+where+" ORDER BY d.id DESC LIMIT 300",args);
    }

    @Transactional
    public Map<String,Object> reviewDeposit(long adminUserId,long depositId,boolean approved,String reason) {
        center.requireAdmin(adminUserId);
        Map<String,Object> row=jdbc.queryForMap("""
                SELECT id,deposit_no AS depositNo,supplier_user_id AS supplierUserId,node_id AS nodeId,
                       gpu_model AS gpuModel,rate_version AS rateVersion,rate_multiplier AS rateMultiplier,
                       standard_card_hours AS standardCardHours,expires_at AS expiresAt,status
                FROM compute_card_hour_deposit WHERE id=? FOR UPDATE
                """,depositId);
        if (!"PENDING".equals(row.get("status"))) throw new BizException(400,"存入申请已经处理");
        long supplier=longValue(row.get("supplierUserId"));
        if (supplier==adminUserId) throw new BizException(400,"存入申请必须由另一名管理员验收");
        if (!approved) {
            jdbc.update("UPDATE compute_card_hour_deposit SET status='REJECTED',rejection_reason=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?",
                    clean(reason,512,"拒绝原因"),adminUserId,depositId);
        } else {
            BigDecimal amount=decimal(row.get("standardCardHours"),3);
            center.credit(supplier,amount,"GPU_DEPOSIT",Objects.toString(row.get("depositNo")),
                    asDateTime(row.get("expiresAt"),null),"已验收 GPU 算力额度按倍率折算存入",adminUserId,
                    "gpu-deposit:"+depositId);
            Long lotId=jdbc.queryForObject("""
                    SELECT id FROM compute_card_hour_lot WHERE owner_user_id=? AND source_type='GPU_DEPOSIT' AND source_ref=?
                    ORDER BY id DESC LIMIT 1
                    """,Long.class,supplier,row.get("depositNo"));
            jdbc.update("""
                    UPDATE compute_card_hour_lot SET gpu_model=?,issuer_user_id=?,node_id=?,rate_version=?,rate_multiplier=? WHERE id=?
                    """,row.get("gpuModel"),supplier,row.get("nodeId"),row.get("rateVersion"),row.get("rateMultiplier"),lotId);
            jdbc.update("UPDATE compute_card_hour_deposit SET status='APPROVED',lot_id=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?",
                    lotId,adminUserId,depositId);
        }
        center.notifyUser(supplier,"CARD_DEPOSIT_REVIEWED",approved?"卡时存入已通过":"卡时存入未通过",
                approved?"验收通过，标准卡时批次已经入账":safe(reason,512),"CARD_DEPOSIT",Objects.toString(row.get("depositNo")));
        return deposit(Objects.toString(row.get("depositNo")));
    }

    @Transactional
    public Map<String,Object> createRedemption(long buyerUserId,RedemptionInput input,boolean autoTopUp) {
        validateSlot(input.startTime(),input.endTime());
        if (input.endTime().isBefore(LocalDateTime.now()) || input.startTime().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new BizException(400,"取出时段必须从当前或未来时间开始");
        }
        Map<String,Object> node=jdbc.queryForMap("""
                SELECT n.id,n.supplier_user_id AS supplierUserId,n.gpu_model AS gpuModel,n.gpu_count AS gpuCount,n.status
                FROM compute_gpu_node n WHERE n.id=? FOR UPDATE
                """,input.nodeId());
        if (!"RUNNING".equals(node.get("status")) || input.gpuCount()<1 || input.gpuCount()>longValue(node.get("gpuCount"))) {
            throw new BizException(400,"GPU 节点不可用或数量无效");
        }
        Long capacity=jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_card_hour_deposit WHERE node_id=? AND status='APPROVED'
                  AND available_from<=? AND available_to>=?
                """,Long.class,input.nodeId(),ts(input.startTime()),ts(input.endTime()));
        if (capacity==null||capacity==0) throw new BizException(409,"该节点时段没有已验收的可取出额度");
        BigDecimal reservedByRedemptions=scalar("""
                SELECT COALESCE(SUM(gpu_count),0) FROM compute_card_hour_redemption
                WHERE node_id=? AND status NOT IN ('COMPLETED','CANCELLED') AND start_time<? AND end_time>?
                """,input.nodeId(),ts(input.endTime()),ts(input.startTime()));
        BigDecimal reservedByLegacyOrders=scalar("""
                SELECT COALESCE(SUM(r.gpu_count),0) FROM compute_reservation r
                JOIN compute_product p ON p.id=r.product_id
                WHERE p.node_id=? AND r.status NOT IN ('CANCELLED','REFUNDED','COMPLETED')
                  AND r.start_time<? AND r.end_time>?
                """,input.nodeId(),ts(input.endTime()),ts(input.startTime()));
        BigDecimal requested=BigDecimal.valueOf(input.gpuCount());
        if (reservedByRedemptions.add(reservedByLegacyOrders).add(requested)
                .compareTo(decimal(node.get("gpuCount"),0))>0) {
            throw new BizException(409,"该 GPU 节点在所选时段的可用数量不足，请调整数量或时间");
        }
        BigDecimal gpuHours=BigDecimal.valueOf(Duration.between(input.startTime(),input.endTime()).toMinutes())
                .divide(new BigDecimal("60"),3,RoundingMode.UP).multiply(BigDecimal.valueOf(input.gpuCount()))
                .setScale(3,RoundingMode.UP);
        Map<String,Object> rate=activeRate(Objects.toString(node.get("gpuModel")));
        BigDecimal multiplier=decimal(rate.get("multiplier"),4);
        String buyerPublicKey=validatedPublicKey(input.buyerPublicKey());
        BigDecimal specificAvailable=scalar("""
                SELECT COALESCE(SUM(remaining_amount-frozen_amount),0) FROM compute_card_hour_lot
                WHERE owner_user_id=? AND asset_type='SPECIFIC' AND UPPER(gpu_model)=UPPER(?)
                  AND (node_id=? OR node_id IS NULL) AND custody_status='ACTIVE'
                  AND remaining_amount>frozen_amount AND (expires_at IS NULL OR expires_at>?)
                """,buyerUserId,node.get("gpuModel"),input.nodeId(),ts(input.endTime()));
        BigDecimal specific=gpuHours.min(specificAvailable).setScale(3,RoundingMode.DOWN);
        BigDecimal standard=scale3(gpuHours.subtract(specific).multiply(multiplier));
        if (standard.compareTo(ZERO3)>0) center.ensureCardHoursForUserAction(buyerUserId,standard,autoTopUp,input.endTime());
        String no=no("CR");
        jdbc.update("""
                INSERT INTO compute_card_hour_redemption(redemption_no,buyer_user_id,supplier_user_id,node_id,gpu_model,
                    gpu_count,start_time,end_time,buyer_public_key,booked_gpu_hours,rate_version,rate_multiplier,
                    specific_hours_frozen,standard_hours_frozen,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING_DELIVERY')
                """,no,buyerUserId,node.get("supplierUserId"),input.nodeId(),node.get("gpuModel"),input.gpuCount(),
                ts(input.startTime()),ts(input.endTime()),buyerPublicKey,gpuHours,rate.get("versionNo"),multiplier,specific,standard);
        long id=Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM compute_card_hour_redemption WHERE redemption_no=?",Long.class,no));
        if (specific.compareTo(ZERO3)>0) freezeSpecificLots(buyerUserId,Objects.toString(node.get("gpuModel")),
                input.nodeId(),specific,input.endTime(),id);
        if (standard.compareTo(ZERO3)>0) freezeStandardLots(buyerUserId,standard,input.endTime(),PURPOSE_REDEMPTION,id,"GPU 取出冻结标准卡时");
        center.notifyUser(longValue(node.get("supplierUserId")),"CARD_REDEMPTION_PENDING","收到新的卡时取出订单",
                no+" 请按时人工交付 GPU 连接信息","CARD_REDEMPTION",no);
        return redemption(id,buyerUserId,false);
    }

    public List<Map<String,Object>> redemptions(long userId,String role) {
        String column="supplier".equalsIgnoreCase(role)?"r.supplier_user_id":"r.buyer_user_id";
        List<Map<String,Object>> rows=jdbc.queryForList(redemptionSql("WHERE "+column+"=? ORDER BY r.id DESC LIMIT 300"),userId);
        rows.forEach(row->decryptForParticipant(row,userId));
        return rows;
    }

    public List<Map<String,Object>> adminRedemptions(long adminUserId) {
        center.requireAdmin(adminUserId);
        List<Map<String,Object>> rows=jdbc.queryForList(redemptionSql("ORDER BY r.id DESC LIMIT 300"));
        rows.forEach(row->decryptForParticipant(row,adminUserId));
        return rows;
    }

    @Transactional
    public Map<String,Object> deliverRedemption(long supplierUserId,long id,DeliveryInput input) {
        Map<String,Object> row=lockRedemption(id);
        if (longValue(row.get("supplierUserId"))!=supplierUserId) throw new BizException(403,"只能交付自己的取出订单");
        if (!"PENDING_DELIVERY".equals(row.get("status"))) throw new BizException(400,"订单当前不能交付");
        String delivery="SSH 地址："+clean(input.sshHost(),255,"SSH 地址")+"\nSSH 端口："+input.sshPort()+
                "\n用户名："+clean(input.sshUsername(),128,"用户名")+"\n交付说明："+safe(input.note(),700);
        jdbc.update("""
                UPDATE compute_card_hour_redemption SET status='DELIVERED',delivery_ciphertext=?,delivery_note=?,delivered_at=NOW()
                WHERE id=?
                """,deliveryCrypto.encrypt(delivery),safe(input.note(),1000),id);
        center.notifyUser(longValue(row.get("buyerUserId")),"CARD_REDEMPTION_DELIVERED","GPU 取出资源已交付",
                row.get("redemptionNo")+" 已交付，请在我的资产中查看","CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        return redemption(id,supplierUserId,false);
    }

    @Transactional
    public Map<String,Object> submitUsage(long supplierUserId,long id,BigDecimal rawActual,String evidence) {
        Map<String,Object> row=lockRedemption(id);
        if (longValue(row.get("supplierUserId"))!=supplierUserId) throw new BizException(403,"只能提交自己的交付凭证");
        if (!"DELIVERED".equals(row.get("status"))) throw new BizException(400,"订单尚未交付或已经提交用量");
        BigDecimal actual=positive3(rawActual,"实际 GPU 卡时");
        if (actual.compareTo(decimal(row.get("bookedGpuHours"),3))>0) {
            jdbc.update("UPDATE compute_card_hour_redemption SET status='PENDING_ACTION',actual_gpu_hours=?,usage_evidence=?,usage_submitted_at=NOW() WHERE id=?",
                    actual,clean(evidence,2000,"交付凭证"),id);
            center.notifyUser(longValue(row.get("buyerUserId")),"CARD_REDEMPTION_OVERTIME","GPU 实际用量超出预订",
                    "供应方已提交超量用量，平台已进入待处理；请补足后再继续使用","CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        } else {
            jdbc.update("""
                    UPDATE compute_card_hour_redemption SET status='USAGE_SUBMITTED',actual_gpu_hours=?,
                        actual_standard_hours=?,usage_evidence=?,usage_submitted_at=NOW(),auto_confirm_at=DATE_ADD(NOW(),INTERVAL 24 HOUR)
                    WHERE id=?
                    """,actual,scale3(actual.multiply(decimal(row.get("rateMultiplier"),4))),
                    clean(evidence,2000,"交付凭证"),id);
            center.notifyUser(longValue(row.get("buyerUserId")),"CARD_USAGE_SUBMITTED","供应方已提交实际用量",
                    "请在 24 小时内确认或发起争议，超时将自动确认","CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        }
        return redemption(id,supplierUserId,false);
    }

    @Transactional
    public Map<String,Object> topUpRedemption(long buyerUserId,long id,boolean autoTopUp) {
        Map<String,Object> row=lockRedemption(id);
        if (longValue(row.get("buyerUserId"))!=buyerUserId) throw new BizException(403,"只能补足自己的取出订单");
        if (!"PENDING_ACTION".equals(row.get("status"))) throw new BizException(400,"订单当前不需要补足卡时");
        BigDecimal actual=decimal(row.get("actualGpuHours"),3);
        BigDecimal booked=decimal(row.get("bookedGpuHours"),3);
        BigDecimal extraGpuHours=actual.subtract(booked).max(ZERO3);
        if (extraGpuHours.compareTo(ZERO3)<=0) throw new BizException(400,"订单没有超出预订的用量");
        BigDecimal extraStandard=scale3(extraGpuHours.multiply(decimal(row.get("rateMultiplier"),4)));
        LocalDateTime validUntil=asDateTime(row.get("endTime"),LocalDateTime.now());
        center.ensureCardHoursForUserAction(buyerUserId,extraStandard,autoTopUp,validUntil);
        freezeStandardLots(buyerUserId,extraStandard,validUntil,PURPOSE_REDEMPTION,id,"GPU 超量用量补足");
        jdbc.update("""
                UPDATE compute_card_hour_redemption SET status='USAGE_SUBMITTED',
                    standard_hours_frozen=standard_hours_frozen+?,actual_standard_hours=?,
                    auto_confirm_at=DATE_ADD(NOW(),INTERVAL 24 HOUR)
                WHERE id=?
                """,extraStandard,scale3(actual.multiply(decimal(row.get("rateMultiplier"),4))),id);
        center.notifyUser(longValue(row.get("supplierUserId")),"CARD_REDEMPTION_TOPPED_UP","GPU 超量用量已补足",
                row.get("redemptionNo")+" 买方已补足 "+extraStandard+" 标准卡时，请停止继续使用并等待买方确认",
                "CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        return redemption(id,buyerUserId,false);
    }

    @Transactional
    public Map<String,Object> confirmRedemption(long buyerUserId,long id) {
        Map<String,Object> row=lockRedemption(id);
        if (longValue(row.get("buyerUserId"))!=buyerUserId) throw new BizException(403,"只能确认自己的取出订单");
        if (!"USAGE_SUBMITTED".equals(row.get("status"))) throw new BizException(400,"供应方尚未提交可确认的用量凭证");
        settleRedemption(row,"BUYER_CONFIRMED");
        return redemption(id,buyerUserId,false);
    }

    @Transactional
    public Map<String,Object> disputeRedemption(long buyerUserId,long id,String reason) {
        Map<String,Object> row=lockRedemption(id);
        if (longValue(row.get("buyerUserId"))!=buyerUserId) throw new BizException(403,"只能对自己的订单发起争议");
        if ("PENDING_DELIVERY".equals(row.get("status"))
                && LocalDateTime.now().isBefore(asDateTime(row.get("startTime"),LocalDateTime.MAX))) {
            throw new BizException(400,"约定开始时间前不能以未交付为由发起争议");
        }
        if (!List.of("PENDING_DELIVERY","DELIVERED","USAGE_SUBMITTED","PENDING_ACTION").contains(Objects.toString(row.get("status")))) {
            throw new BizException(400,"订单当前不能发起争议");
        }
        jdbc.update("UPDATE compute_card_hour_redemption SET status='DISPUTED',dispute_reason=? WHERE id=?",
                clean(reason,1000,"争议原因"),id);
        notifyAdmins("CARD_REDEMPTION_DISPUTED","卡时取出订单发生争议",Objects.toString(row.get("redemptionNo")),
                "CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        return redemption(id,buyerUserId,false);
    }

    @Transactional
    public Map<String,Object> resolveRedemption(long adminUserId,long id,BigDecimal actualHours,String reason) {
        center.requireAdmin(adminUserId);
        Map<String,Object> row=lockRedemption(id);
        if (!List.of("DISPUTED","PENDING_ACTION").contains(Objects.toString(row.get("status")))) {
            throw new BizException(400,"订单不在待处理状态");
        }
        BigDecimal actual=decimal(actualHours,3);
        if (actual.compareTo(ZERO3)<0 || actual.compareTo(decimal(row.get("bookedGpuHours"),3))>0) {
            throw new BizException(400,"管理员裁定用量必须在已冻结范围内");
        }
        jdbc.update("UPDATE compute_card_hour_redemption SET actual_gpu_hours=?,actual_standard_hours=?,usage_evidence=CONCAT(usage_evidence,'；管理员裁定：',?) WHERE id=?",
                actual,scale3(actual.multiply(decimal(row.get("rateMultiplier"),4))),safe(reason,512),id);
        row=lockRedemption(id);
        settleRedemption(row,"ADMIN_RESOLVED");
        return redemption(id,adminUserId,true);
    }

    @Transactional
    public Map<String,Object> upsertRate(long adminUserId,String versionNo,String gpuModel,BigDecimal multiplier,String notes) {
        center.requireAdmin(adminUserId);
        String version=clean(versionNo,32,"倍率版本").toUpperCase(Locale.ROOT);
        String model=clean(gpuModel,128,"GPU 型号");
        BigDecimal rate=positive4(multiplier,"倍率");
        jdbc.update("UPDATE compute_card_hour_rate_rule SET status='RETIRED' WHERE UPPER(gpu_model)=UPPER(?) AND status='ACTIVE'",model);
        try {
            jdbc.update("""
                    INSERT INTO compute_card_hour_rate_rule(version_no,gpu_model,multiplier,status,effective_from,created_by,notes)
                    VALUES (?,?,?,'ACTIVE',NOW(),?,?)
                    """,version,model,rate,adminUserId,safe(notes,512));
        } catch (DuplicateKeyException e) {
            throw new BizException(400,"该版本和 GPU 型号的倍率已经存在");
        }
        return activeRate(model);
    }

    @Transactional
    public void advanceScheduledWork() {
        List<Long> expiredListings=jdbc.queryForList("""
                SELECT id FROM compute_card_hour_listing WHERE status IN ('PUBLISHED','QUOTE_RESERVED')
                  AND listing_expires_at IS NOT NULL AND listing_expires_at<=NOW()
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """,Long.class);
        for (Long id:expiredListings) expireListing(id);
        jdbc.update("UPDATE compute_card_hour_purchase_quote SET status='EXPIRED' WHERE status='LOCKED' AND expires_at<=NOW()");
        jdbc.update("UPDATE compute_card_hour_rfq SET status='EXPIRED' WHERE status='OPEN' AND closes_at<=NOW()");

        List<Map<String,Object>> quoteListings=jdbc.queryForList("""
                SELECT q.id,q.listing_id AS listingId,l.seller_user_id AS sellerUserId
                FROM compute_card_hour_rfq_quote q JOIN compute_card_hour_listing l ON l.id=q.listing_id
                WHERE q.status='ACTIVE' AND q.expires_at<=NOW() ORDER BY q.id LIMIT 100 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String,Object> q:quoteListings) {
            jdbc.update("UPDATE compute_card_hour_rfq_quote SET status='EXPIRED' WHERE id=?",q.get("id"));
            jdbc.update("UPDATE compute_card_hour_listing SET status='EXPIRED' WHERE id=?",q.get("listingId"));
            releasePurpose(longValue(q.get("sellerUserId")),PURPOSE_LISTING,longValue(q.get("listingId")),"询价报价到期解冻");
        }
        List<Map<String,Object>> expiredAccess=jdbc.queryForList("""
                SELECT id,buyer_user_id AS buyerUserId,supplier_user_id AS supplierUserId,
                       redemption_no AS redemptionNo FROM compute_card_hour_redemption
                WHERE status='DELIVERED' AND end_time<=NOW() AND stop_reminded_at IS NULL
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String,Object> row:expiredAccess) {
            jdbc.update("UPDATE compute_card_hour_redemption SET stop_reminded_at=NOW() WHERE id=?",row.get("id"));
            center.notifyUser(longValue(row.get("supplierUserId")),"CARD_REDEMPTION_STOP_DUE","GPU 订单已到期，请停止访问",
                    row.get("redemptionNo")+" 已到期，请立即移除买方公钥或停用订单专属账号，并提交实际用量凭证",
                    "CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
            center.notifyUser(longValue(row.get("buyerUserId")),"CARD_REDEMPTION_STOP_DUE","GPU 订单已到期",
                    row.get("redemptionNo")+" 已到期，请停止使用；若供应方未按约处理可在订单中发起争议",
                    "CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        }
        convertExpiredMarketLots();
        List<Long> auto=jdbc.queryForList("""
                SELECT id FROM compute_card_hour_redemption WHERE status='USAGE_SUBMITTED' AND auto_confirm_at<=NOW()
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """,Long.class);
        for (Long id:auto) settleRedemption(lockRedemption(id),"AUTO_CONFIRM_24H");
    }

    private void convertExpiredMarketLots() {
        List<Map<String,Object>> lots=jdbc.queryForList("""
                SELECT id,owner_user_id AS ownerUserId,asset_type AS assetType,gpu_model AS gpuModel,
                       source_type AS sourceType,remaining_amount AS remainingAmount,frozen_amount AS frozenAmount
                FROM compute_card_hour_lot WHERE expires_at<=NOW() AND remaining_amount>frozen_amount
                  AND custody_status='ACTIVE' AND (asset_type='SPECIFIC' OR source_type IN
                    ('GPU_DEPOSIT','CARD_MARKET_SALE','CARD_MARKET_TRANSFER','SPECIFIC_EXPIRY_CONVERSION','STANDARD_ROLLOVER'))
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String,Object> lot:lots) {
            BigDecimal available=available(lot);
            long user=longValue(lot.get("ownerUserId"));
            if (SPECIFIC.equals(lot.get("assetType"))) {
                Map<String,Object> rate=activeRate(Objects.toString(lot.get("gpuModel")));
                BigDecimal converted=scale3(available.multiply(decimal(rate.get("multiplier"),4)));
                jdbc.update("UPDATE compute_card_hour_lot SET remaining_amount=frozen_amount,custody_status=CASE WHEN frozen_amount=0 THEN 'CONVERTED' ELSE custody_status END WHERE id=?",lot.get("id"));
                center.credit(user,converted,"SPECIFIC_EXPIRY_CONVERSION",Objects.toString(lot.get("id")),null,
                        "指定 GPU 卡时到期按当期规则自动置换为标准卡时",null,"specific-expiry:"+lot.get("id"));
                center.notifyUser(user,"CARD_LOT_CONVERTED","指定 GPU 卡时已自动置换",
                        available+" 指定卡时已按当前倍率置换为 "+converted+" 标准卡时","CARD_LOT",Objects.toString(lot.get("id")));
            } else {
                jdbc.update("UPDATE compute_card_hour_lot SET expires_at=NULL,source_type='STANDARD_ROLLOVER',source_ref=CONCAT('到期续存:',id) WHERE id=?",lot.get("id"));
                center.notifyUser(user,"CARD_LOT_ROLLED_OVER","标准卡时批次已到期续存",
                        available+" 标准卡时已转入永久有效批次","CARD_LOT",Objects.toString(lot.get("id")));
            }
        }
    }

    private long deliverListedAsset(Map<String,Object> listing,long buyerUserId) {
        long seller=longValue(listing.get("sellerUserId"));
        List<Map<String,Object>> allocations=allocations(PURPOSE_LISTING,longValue(listing.get("id")));
        if (allocations.isEmpty()) throw new BizException(409,"商品冻结批次不存在");
        BigDecimal collateral=allocations.stream().map(a->decimal(a.get("amount"),3)).reduce(ZERO3,BigDecimal::add);
        for (Map<String,Object> a:allocations) {
            jdbc.update("UPDATE compute_card_hour_lot SET remaining_amount=remaining_amount-?,frozen_amount=frozen_amount-? WHERE id=?",
                    a.get("amount"),a.get("amount"),a.get("lotId"));
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",PURPOSE_LISTING,listing.get("id"));
        if (STANDARD.equals(allocations.get(0).get("assetType"))) {
            jdbc.update("UPDATE compute_account SET frozen_card_hours=frozen_card_hours-?,lifetime_consumption=lifetime_consumption+?,version=version+1 WHERE user_id=?",
                    collateral,collateral,seller);
        }
        BigDecimal quantity=decimal(listing.get("quantity"),3);
        String buyerAsset=Objects.toString(listing.get("assetType"));
        jdbc.update("""
                INSERT INTO compute_card_hour_lot(owner_user_id,asset_type,gpu_model,issuer_user_id,node_id,
                    rate_version,rate_multiplier,custody_status,parent_lot_id,source_type,source_ref,
                    original_amount,remaining_amount,frozen_amount,expires_at)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?,'CARD_MARKET_SALE',?,?,?,0.000,?)
                """,buyerUserId,buyerAsset,listing.get("gpuModel"),seller,listing.get("nodeId"),listing.get("rateVersion"),
                listing.get("rateMultiplier"),listing.get("sourceLotId"),Objects.toString(listing.get("listingNo")),
                quantity,quantity,listing.get("assetExpiresAt"));
        long lotId=Objects.requireNonNull(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
        if (STANDARD.equals(buyerAsset)) {
            center.ensureAccount(buyerUserId);
            jdbc.update("UPDATE compute_account SET available_card_hours=available_card_hours+?,version=version+1 WHERE user_id=?",quantity,buyerUserId);
            ledger(buyerUserId,"CARD_MARKET_IN","CREDIT",quantity,"CARD_LISTING",Objects.toString(listing.get("listingNo")),"卡时市场购入批次");
        }
        return lotId;
    }

    private void settleRedemption(Map<String,Object> row,String resolution) {
        BigDecimal actual=decimal(row.get("actualGpuHours"),3);
        BigDecimal remainingActual=actual;
        long buyer=longValue(row.get("buyerUserId"));
        long supplier=longValue(row.get("supplierUserId"));
        List<Map<String,Object>> allocations=allocations(PURPOSE_REDEMPTION,longValue(row.get("id")));
        BigDecimal standardConsumed=ZERO3;
        BigDecimal standardFrozen=ZERO3;
        for (Map<String,Object> a:allocations) {
            BigDecimal allocated=decimal(a.get("amount"),3);
            boolean specific=SPECIFIC.equals(a.get("assetType"));
            BigDecimal consume;
            if (specific) {
                consume=allocated.min(remainingActual);
                remainingActual=remainingActual.subtract(consume);
            } else {
                standardFrozen=standardFrozen.add(allocated);
                BigDecimal needed=scale3(remainingActual.multiply(decimal(row.get("rateMultiplier"),4)));
                consume=allocated.min(needed);
                standardConsumed=standardConsumed.add(consume);
                if (decimal(row.get("rateMultiplier"),4).compareTo(BigDecimal.ZERO)>0) {
                    remainingActual=remainingActual.subtract(consume.divide(decimal(row.get("rateMultiplier"),4),3,RoundingMode.DOWN)).max(ZERO3);
                }
            }
            jdbc.update("UPDATE compute_card_hour_lot SET remaining_amount=remaining_amount-?,frozen_amount=frozen_amount-? WHERE id=?",
                    consume,allocated,a.get("lotId"));
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",PURPOSE_REDEMPTION,row.get("id"));
        BigDecimal standardRefund=standardFrozen.subtract(standardConsumed).max(ZERO3);
        if (standardFrozen.compareTo(ZERO3)>0) {
            jdbc.update("UPDATE compute_account SET available_card_hours=available_card_hours+?,frozen_card_hours=frozen_card_hours-?,lifetime_consumption=lifetime_consumption+?,version=version+1 WHERE user_id=?",
                    standardRefund,standardFrozen,standardConsumed,buyer);
            if (standardConsumed.compareTo(ZERO3)>0) ledger(buyer,"GPU_REDEMPTION","DEBIT",standardConsumed,"CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")),"GPU 实际用量结算");
            if (standardRefund.compareTo(ZERO3)>0) ledger(buyer,"GPU_REDEMPTION_REFUND","CREDIT",standardRefund,"CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")),"未使用冻结卡时退回");
        }
        if (standardConsumed.compareTo(ZERO3)>0) center.credit(supplier,standardConsumed,"GPU_RENTAL_INCOME",
                Objects.toString(row.get("redemptionNo")),null,"卡时取出实际用量结算",null,"redemption-income:"+row.get("id"));
        jdbc.update("UPDATE compute_card_hour_redemption SET status='COMPLETED',actual_standard_hours=?,completed_at=NOW(),delivery_note=CONCAT(delivery_note,'；结算方式:',?) WHERE id=?",
                standardConsumed,resolution,row.get("id"));
        center.notifyUser(buyer,"CARD_REDEMPTION_COMPLETED","GPU 取出订单已完成",
                "实际使用 "+actual+" GPU 卡时，未使用额度已退回","CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
        center.notifyUser(supplier,"CARD_REDEMPTION_COMPLETED","GPU 取出订单已结算",
                "标准卡时结算收入 "+standardConsumed,"CARD_REDEMPTION",Objects.toString(row.get("redemptionNo")));
    }

    private void freezeExactLot(long userId,long lotId,BigDecimal amount,String purpose,long purposeId,
                                LocalDateTime validUntil,String description) {
        Map<String,Object> lot=lockLot(userId,lotId);
        if (available(lot).compareTo(amount)<0 || (lot.get("expiresAt")!=null
                && !asDateTime(lot.get("expiresAt"),LocalDateTime.MIN).isAfter(validUntil))) {
            throw new BizException(400,"来源批次余额不足或无法覆盖业务有效期");
        }
        jdbc.update("UPDATE compute_card_hour_lot SET frozen_amount=frozen_amount+? WHERE id=?",amount,lotId);
        jdbc.update("INSERT INTO compute_freeze_allocation(purpose_type,purpose_id,lot_id,amount,expires_at) VALUES (?,?,?,?,?)",
                purpose,purposeId,lotId,amount,lot.get("expiresAt"));
        if (STANDARD.equals(lot.get("assetType"))) {
            center.ensureAccount(userId);
            jdbc.update("UPDATE compute_account SET available_card_hours=available_card_hours-?,frozen_card_hours=frozen_card_hours+?,version=version+1 WHERE user_id=?",
                    amount,amount,userId);
            ledger(userId,"FREEZE","DEBIT",amount,purpose,Long.toString(purposeId),description);
        }
    }

    private void freezeStandardLots(long userId,BigDecimal amount,LocalDateTime validUntil,String purpose,long purposeId,String description) {
        BigDecimal remaining=amount;
        List<Map<String,Object>> lots=jdbc.queryForList("""
                SELECT id,asset_type AS assetType,remaining_amount AS remainingAmount,frozen_amount AS frozenAmount,expires_at AS expiresAt
                FROM compute_card_hour_lot WHERE owner_user_id=? AND asset_type='STANDARD' AND custody_status='ACTIVE'
                  AND remaining_amount>frozen_amount AND (expires_at IS NULL OR expires_at>?)
                ORDER BY CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END,expires_at,id FOR UPDATE
                """,userId,ts(validUntil));
        for (Map<String,Object> lot:lots) {
            BigDecimal take=available(lot).min(remaining);
            if (take.compareTo(ZERO3)<=0) continue;
            jdbc.update("UPDATE compute_card_hour_lot SET frozen_amount=frozen_amount+? WHERE id=?",take,lot.get("id"));
            jdbc.update("INSERT INTO compute_freeze_allocation(purpose_type,purpose_id,lot_id,amount,expires_at) VALUES (?,?,?,?,?)",
                    purpose,purposeId,lot.get("id"),take,lot.get("expiresAt"));
            remaining=remaining.subtract(take);
            if (remaining.compareTo(ZERO3)<=0) break;
        }
        if (remaining.compareTo(ZERO3)>0) throw new BizException(409,"可用标准卡时批次不足");
        jdbc.update("UPDATE compute_account SET available_card_hours=available_card_hours-?,frozen_card_hours=frozen_card_hours+?,version=version+1 WHERE user_id=?",
                amount,amount,userId);
        ledger(userId,"FREEZE","DEBIT",amount,purpose,Long.toString(purposeId),description);
    }

    private void freezeSpecificLots(long userId,String gpuModel,long nodeId,BigDecimal amount,LocalDateTime validUntil,long purposeId) {
        BigDecimal remaining=amount;
        List<Map<String,Object>> lots=jdbc.queryForList("""
                SELECT id,asset_type AS assetType,remaining_amount AS remainingAmount,frozen_amount AS frozenAmount,expires_at AS expiresAt
                FROM compute_card_hour_lot WHERE owner_user_id=? AND asset_type='SPECIFIC' AND UPPER(gpu_model)=UPPER(?)
                  AND (node_id=? OR node_id IS NULL) AND custody_status='ACTIVE' AND remaining_amount>frozen_amount
                  AND (expires_at IS NULL OR expires_at>?) ORDER BY expires_at,id FOR UPDATE
                """,userId,gpuModel,nodeId,ts(validUntil));
        for (Map<String,Object> lot:lots) {
            BigDecimal take=available(lot).min(remaining);
            if (take.compareTo(ZERO3)<=0) continue;
            jdbc.update("UPDATE compute_card_hour_lot SET frozen_amount=frozen_amount+? WHERE id=?",take,lot.get("id"));
            jdbc.update("INSERT INTO compute_freeze_allocation(purpose_type,purpose_id,lot_id,amount,expires_at) VALUES (?,?,?,?,?)",
                    PURPOSE_REDEMPTION,purposeId,lot.get("id"),take,lot.get("expiresAt"));
            remaining=remaining.subtract(take);
            if (remaining.compareTo(ZERO3)<=0) break;
        }
        if (remaining.compareTo(ZERO3)>0) throw new BizException(409,"指定 GPU 卡时批次不足");
    }

    private void releasePurpose(long userId,String purpose,long purposeId,String description) {
        List<Map<String,Object>> allocations=allocations(purpose,purposeId);
        BigDecimal standard=ZERO3;
        for (Map<String,Object> a:allocations) {
            jdbc.update("UPDATE compute_card_hour_lot SET frozen_amount=frozen_amount-? WHERE id=?",a.get("amount"),a.get("lotId"));
            if (STANDARD.equals(a.get("assetType"))) standard=standard.add(decimal(a.get("amount"),3));
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",purpose,purposeId);
        if (standard.compareTo(ZERO3)>0) {
            jdbc.update("UPDATE compute_account SET available_card_hours=available_card_hours+?,frozen_card_hours=frozen_card_hours-?,version=version+1 WHERE user_id=?",
                    standard,standard,userId);
            ledger(userId,"UNFREEZE","CREDIT",standard,purpose,Long.toString(purposeId),description);
        }
    }

    private List<Map<String,Object>> allocations(String purpose,long purposeId) {
        return jdbc.queryForList("""
                SELECT a.id,a.lot_id AS lotId,a.amount,l.asset_type AS assetType,l.gpu_model AS gpuModel
                FROM compute_freeze_allocation a JOIN compute_card_hour_lot l ON l.id=a.lot_id
                WHERE a.purpose_type=? AND a.purpose_id=? ORDER BY CASE WHEN l.asset_type='SPECIFIC' THEN 0 ELSE 1 END,a.id FOR UPDATE
                """,purpose,purposeId);
    }

    private Map<String,Object> activeRate(String gpuModel) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT id,version_no AS versionNo,gpu_model AS gpuModel,multiplier,status,effective_from AS effectiveFrom,notes
                FROM compute_card_hour_rate_rule WHERE UPPER(gpu_model)=UPPER(?) AND status='ACTIVE'
                ORDER BY effective_from DESC,id DESC LIMIT 1
                """,gpuModel);
        if (rows.isEmpty()) throw new BizException(400,"GPU 型号尚未配置标准卡时折算倍率，请联系管理员");
        return rows.get(0);
    }

    private void requireApprovedSupplier(long userId) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM compute_supplier WHERE user_id=? AND status='APPROVED'",Long.class,userId);
        if (count==null||count==0) throw new BizException(403,"只有已认证供应方可以销售卡时或参与询价报价");
    }

    private Map<String,Object> lockLot(long userId,long lotId) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT id,owner_user_id AS ownerUserId,asset_type AS assetType,gpu_model AS gpuModel,node_id AS nodeId,
                       source_type AS sourceType,source_ref AS sourceRef,remaining_amount AS remainingAmount,
                       frozen_amount AS frozenAmount,rate_version AS rateVersion,rate_multiplier AS rateMultiplier,
                       custody_status AS custodyStatus,expires_at AS expiresAt
                FROM compute_card_hour_lot WHERE id=? AND owner_user_id=? FOR UPDATE
                """,lotId,userId);
        if (rows.isEmpty()) throw new BizException(404,"卡时批次不存在");
        Map<String,Object> lot=rows.get(0);
        if (!"ACTIVE".equals(lot.get("custodyStatus"))) throw new BizException(400,"卡时批次当前不可用");
        return lot;
    }

    private Map<String,Object> lockListing(long id) {
        return jdbc.queryForMap("""
                SELECT id,listing_no AS listingNo,seller_user_id AS sellerUserId,market_type AS marketType,
                       asset_type AS assetType,gpu_model AS gpuModel,node_id AS nodeId,source_lot_id AS sourceLotId,
                       quantity,unit_price AS unitPrice,price_currency AS priceCurrency,
                       collateral_card_hours AS collateralCardHours,asset_expires_at AS assetExpiresAt,
                       listing_expires_at AS listingExpiresAt,rate_version AS rateVersion,
                       rate_multiplier AS rateMultiplier,title,description,status
                FROM compute_card_hour_listing WHERE id=? FOR UPDATE
                """,id);
    }

    private Map<String,Object> listing(long id) {
        return jdbc.queryForMap("""
                SELECT id,listing_no AS listingNo,seller_user_id AS sellerUserId,market_type AS marketType,
                       asset_type AS assetType,gpu_model AS gpuModel,node_id AS nodeId,source_lot_id AS sourceLotId,
                       quantity,unit_price AS unitPrice,price_currency AS priceCurrency,asset_expires_at AS assetExpiresAt,
                       listing_expires_at AS listingExpiresAt,rate_version AS rateVersion,rate_multiplier AS rateMultiplier,
                       title,description,status,create_time AS createTime FROM compute_card_hour_listing WHERE id=?
                """,id);
    }

    private Map<String,Object> quoteByNo(String no) {
        return jdbc.queryForMap("""
                SELECT id,quote_no AS quoteNo,listing_id AS listingId,buyer_user_id AS buyerUserId,quantity,
                       unit_price_snapshot AS unitPrice,price_currency AS priceCurrency,total_price AS totalPrice,
                       cny_rate_snapshot AS cnyRate,buyer_fee_card_hours AS buyerFee,
                       seller_fee_card_hours AS sellerFee,asset_expires_at AS assetExpiresAt,status,
                       expires_at AS expiresAt,create_time AS createTime
                FROM compute_card_hour_purchase_quote WHERE quote_no=?
                """,no);
    }

    private Map<String,Object> trade(String no) {
        return jdbc.queryForMap("""
                SELECT id,trade_no AS tradeNo,listing_id AS listingId,buyer_user_id AS buyerUserId,
                       seller_user_id AS sellerUserId,market_type AS marketType,asset_type AS assetType,
                       gpu_model AS gpuModel,quantity,unit_price AS unitPrice,price_currency AS priceCurrency,
                       total_price AS totalPrice,buyer_fee_card_hours AS buyerFee,seller_fee_card_hours AS sellerFee,
                       cny_amount AS cnyAmount,buyer_lot_id AS buyerLotId,status,completed_at AS completedAt
                FROM compute_card_hour_trade WHERE trade_no=?
                """,no);
    }

    private Map<String,Object> rfqByNo(String no) {
        return jdbc.queryForMap("""
                SELECT id,rfq_no AS rfqNo,buyer_user_id AS buyerUserId,asset_type AS assetType,gpu_model AS gpuModel,
                       quantity,minimum_expires_at AS minimumExpiresAt,requirements,status,
                       selected_quote_id AS selectedQuoteId,closes_at AS closesAt,create_time AS createTime
                FROM compute_card_hour_rfq WHERE rfq_no=?
                """,no);
    }

    private Map<String,Object> rfqQuote(String no) {
        return jdbc.queryForMap("""
                SELECT id,quote_no AS quoteNo,rfq_id AS rfqId,supplier_user_id AS supplierUserId,
                       listing_id AS listingId,unit_price AS unitPrice,price_currency AS priceCurrency,
                       asset_expires_at AS assetExpiresAt,status,expires_at AS expiresAt,create_time AS createTime
                FROM compute_card_hour_rfq_quote WHERE quote_no=?
                """,no);
    }

    private Map<String,Object> deposit(String no) {
        return jdbc.queryForMap("""
                SELECT id,deposit_no AS depositNo,supplier_user_id AS supplierUserId,node_id AS nodeId,
                       gpu_model AS gpuModel,gpu_count AS gpuCount,available_from AS availableFrom,
                       available_to AS availableTo,expires_at AS expiresAt,gpu_hours AS gpuHours,
                       rate_version AS rateVersion,rate_multiplier AS rateMultiplier,standard_card_hours AS standardCardHours,
                       status,lot_id AS lotId,rejection_reason AS rejectionReason,reviewed_at AS reviewedAt,create_time AS createTime
                FROM compute_card_hour_deposit WHERE deposit_no=?
                """,no);
    }

    private Map<String,Object> lockRedemption(long id) {
        return jdbc.queryForMap(redemptionSql("WHERE r.id=? FOR UPDATE"),id);
    }

    private Map<String,Object> redemption(long id,long userId,boolean admin) {
        if (!admin) {
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM compute_card_hour_redemption WHERE id=? AND (buyer_user_id=? OR supplier_user_id=?)",Long.class,id,userId,userId);
            if (count==null||count==0) throw new BizException(403,"无权查看该取出订单");
        }
        Map<String,Object> row=jdbc.queryForMap(redemptionSql("WHERE r.id=?"),id);
        decryptForParticipant(row,userId);
        return row;
    }

    private String redemptionSql(String suffix) {
        return """
                SELECT r.id,r.redemption_no AS redemptionNo,r.buyer_user_id AS buyerUserId,
                       r.supplier_user_id AS supplierUserId,r.node_id AS nodeId,r.gpu_model AS gpuModel,
                       r.gpu_count AS gpuCount,r.start_time AS startTime,r.end_time AS endTime,
                       r.buyer_public_key AS buyerPublicKey,
                       r.booked_gpu_hours AS bookedGpuHours,r.rate_version AS rateVersion,
                       r.rate_multiplier AS rateMultiplier,r.specific_hours_frozen AS specificHoursFrozen,
                       r.standard_hours_frozen AS standardHoursFrozen,r.actual_gpu_hours AS actualGpuHours,
                       r.actual_standard_hours AS actualStandardHours,r.status,r.delivery_ciphertext AS deliveryCiphertext,
                       r.delivery_note AS deliveryNote,r.usage_evidence AS usageEvidence,r.dispute_reason AS disputeReason,
                       r.delivered_at AS deliveredAt,r.usage_submitted_at AS usageSubmittedAt,
                       r.stop_reminded_at AS stopRemindedAt,
                       r.auto_confirm_at AS autoConfirmAt,r.completed_at AS completedAt,r.create_time AS createTime,
                       bu.email AS buyerEmail,su.email AS supplierEmail,n.node_name AS nodeName
                FROM compute_card_hour_redemption r JOIN sys_user bu ON bu.id=r.buyer_user_id
                JOIN sys_user su ON su.id=r.supplier_user_id JOIN compute_gpu_node n ON n.id=r.node_id
                """+suffix;
    }

    private void decryptForParticipant(Map<String,Object> row,long userId) {
        Object cipher=row.remove("deliveryCiphertext");
        if (cipher!=null && (longValue(row.get("buyerUserId"))==userId || longValue(row.get("supplierUserId"))==userId || center.isAdmin(userId))) {
            row.put("deliveryInfo",deliveryCrypto.decrypt(cipher.toString()));
        } else row.put("deliveryInfo","");
        row.put("buyerEmail",maskEmail(Objects.toString(row.get("buyerEmail"),"")));
        row.put("supplierEmail",maskEmail(Objects.toString(row.get("supplierEmail"),"")));
    }

    private void expireListing(long id) {
        Map<String,Object> listing=lockListing(id);
        if (!List.of("PUBLISHED","QUOTE_RESERVED").contains(Objects.toString(listing.get("status")))) return;
        jdbc.update("UPDATE compute_card_hour_listing SET status='EXPIRED' WHERE id=?",id);
        releasePurpose(longValue(listing.get("sellerUserId")),PURPOSE_LISTING,id,"卡时商品到期解冻");
        center.notifyUser(longValue(listing.get("sellerUserId")),"CARD_LISTING_EXPIRED","卡时商品已到期",
                Objects.toString(listing.get("listingNo"))+" 已下架并解冻库存","CARD_LISTING",Objects.toString(listing.get("listingNo")));
    }

    private void releaseOtherRfqListings(long rfqId,long acceptedListingId) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT q.listing_id AS listingId,l.seller_user_id AS sellerUserId FROM compute_card_hour_rfq_quote q
                JOIN compute_card_hour_listing l ON l.id=q.listing_id WHERE q.rfq_id=? AND q.listing_id<>?
                """,rfqId,acceptedListingId);
        for (Map<String,Object> row:rows) {
            jdbc.update("UPDATE compute_card_hour_listing SET status='CANCELLED' WHERE id=? AND status='QUOTE_RESERVED'",row.get("listingId"));
            releasePurpose(longValue(row.get("sellerUserId")),PURPOSE_LISTING,longValue(row.get("listingId")),"询价未中选解冻");
        }
    }

    private void recordFees(String tradeNo,long buyer,long seller) {
        jdbc.update("INSERT INTO compute_card_hour_fee_ledger(reference_type,reference_id,payer_user_id,side,fee_card_hours) VALUES ('CARD_TRADE',?,?,'BUYER',?)",
                tradeNo,buyer,HALF_TRADE_FEE);
        jdbc.update("INSERT INTO compute_card_hour_fee_ledger(reference_type,reference_id,payer_user_id,side,fee_card_hours) VALUES ('CARD_TRADE',?,?,'SELLER',?)",
                tradeNo,seller,HALF_TRADE_FEE);
    }

    private void ledger(long userId,String entryType,String direction,BigDecimal amount,String referenceType,String referenceId,String description) {
        Map<String,Object> account=jdbc.queryForMap("SELECT available_card_hours AS availableCardHours,frozen_card_hours AS frozenCardHours FROM compute_account WHERE user_id=? FOR UPDATE",userId);
        jdbc.update("""
                INSERT INTO compute_ledger(user_id,entry_type,direction,amount,available_after,frozen_after,
                    reference_type,reference_id,description,idempotency_key)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,userId,entryType,direction,amount,account.get("availableCardHours"),account.get("frozenCardHours"),
                referenceType,referenceId,description,entryType+":"+referenceId+":"+direction);
    }

    private void notifyApprovedSuppliers(String type,String title,String content,String referenceType,String referenceId) {
        jdbc.queryForList("SELECT user_id FROM compute_supplier WHERE status='APPROVED'",Long.class)
                .forEach(id->center.notifyUser(id,type,title,content,referenceType,referenceId));
    }

    private void notifyAdmins(String type,String title,String content,String referenceType,String referenceId) {
        jdbc.queryForList("SELECT id FROM sys_user",Long.class).stream().filter(center::isAdmin)
                .forEach(id->center.notifyUser(id,type,title,content,referenceType,referenceId));
    }

    private void maskSeller(Map<String,Object> row) {
        row.put("sellerEmail",maskEmail(Objects.toString(row.get("sellerEmail"),"")));
        row.put("sellerName",maskName(Objects.toString(row.get("sellerName"),"")));
    }

    private void validateSlot(LocalDateTime from,LocalDateTime to) {
        if (from==null||to==null||!to.isAfter(from)||Duration.between(from,to).toMinutes()<60) {
            throw new BizException(400,"算力时段至少为 1 小时且结束时间必须晚于开始时间");
        }
    }

    private LocalDateTime requireMarketExpiry(LocalDateTime expiry) {
        if (expiry==null||expiry.isBefore(LocalDateTime.now().plusDays(7))) {
            throw new BizException(400,"卡时批次或商品有效期距离当前时间不得少于 7 天");
        }
        return expiry;
    }

    private BigDecimal setting(String key,BigDecimal fallback) {
        List<String> values=jdbc.query("SELECT setting_value FROM compute_setting WHERE setting_key=?",
                (rs,n)->rs.getString(1),key);
        return values.isEmpty()?fallback:new BigDecimal(values.get(0));
    }

    private BigDecimal scalar(String sql,Object...args) {
        BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class,args);
        return value==null?ZERO3:value;
    }

    private static BigDecimal available(Map<String,Object> lot) {
        return decimal(lot.get("remainingAmount"),3).subtract(decimal(lot.get("frozenAmount"),3));
    }

    private static BigDecimal positive3(BigDecimal value,String label) {
        BigDecimal result=decimal(value,3);
        if (result.compareTo(ZERO3)<=0) throw new BizException(400,label+"必须大于 0");
        return result;
    }

    private static BigDecimal positive4(BigDecimal value,String label) {
        BigDecimal result=decimal(value,4);
        if (result.compareTo(BigDecimal.ZERO)<=0) throw new BizException(400,label+"必须大于 0");
        return result;
    }

    private static BigDecimal scale3(BigDecimal value) {
        if (value==null) throw new BizException(400,"卡时数量不能为空");
        return value.setScale(3,RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Object value,int scale) {
        if (value==null) return BigDecimal.ZERO.setScale(scale,RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(scale,RoundingMode.HALF_UP);
    }

    private static String upper(String value) {
        return Objects.toString(value,"").trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String value,int max,String label) {
        String result=Objects.toString(value,"").trim();
        if (result.isEmpty()) throw new BizException(400,label+"不能为空");
        if (result.length()>max) throw new BizException(400,label+"过长");
        return result;
    }

    private static String safe(String value,int max) {
        String result=Objects.toString(value,"").trim();
        return result.length()>max?result.substring(0,max):result;
    }

    private static String validatedPublicKey(String value) {
        String key=clean(value,16000,"买方 SSH 公钥");
        int firstSpace=key.indexOf(' ');
        String type=firstSpace>0?key.substring(0,firstSpace):"";
        String body=firstSpace>0?key.substring(firstSpace+1).trim().split("\\s+",2)[0]:"";
        boolean allowedType=type.equals("ssh-rsa") || type.equals("ssh-ed25519")
                || type.startsWith("ecdsa-sha2-") || type.startsWith("sk-");
        if (key.contains("PRIVATE KEY") || key.contains("\n") || key.contains("\r") || !allowedType
                || body.length()<32 || !body.matches("[A-Za-z0-9+/=]+")) {
            throw new BizException(400,"请提交单行 OpenSSH 公钥，禁止提交任何私钥");
        }
        return key;
    }

    private static String no(String prefix) {
        return prefix+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(Locale.ROOT);
    }

    private static long longValue(Object value) {
        return value instanceof Number n?n.longValue():Long.parseLong(value.toString());
    }

    private static Long longOrNull(Object value) {
        return value==null?null:longValue(value);
    }

    private static Timestamp ts(LocalDateTime value) {
        return value==null?null:Timestamp.valueOf(value);
    }

    private static LocalDateTime asDateTime(Object value,LocalDateTime fallback) {
        if (value==null) return fallback;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime time) return time;
        return LocalDateTime.parse(value.toString().replace(' ','T'));
    }

    private static String maskEmail(String email) {
        int at=email.indexOf('@');
        if (at<=0) return "***";
        return email.substring(0,Math.min(2,at))+"***"+email.substring(at);
    }

    private static String maskName(String name) {
        if (name.isBlank()) return "已认证供应方";
        return name.substring(0,1)+"***";
    }

    public record ListingInput(String marketType,String assetType,String gpuModel,long sourceLotId,
                               BigDecimal quantity,BigDecimal unitPrice,LocalDateTime assetExpiresAt,
                               LocalDateTime listingExpiresAt,String title,String description) {}
    public record RfqInput(String assetType,String gpuModel,BigDecimal quantity,LocalDateTime minimumExpiresAt,
                           LocalDateTime closesAt,String requirements) {}
    public record RfqQuoteInput(long sourceLotId,BigDecimal unitPrice) {}
    public record DepositInput(long nodeId,LocalDateTime availableFrom,LocalDateTime availableTo,
                               LocalDateTime expiresAt) {}
    public record RedemptionInput(long nodeId,int gpuCount,LocalDateTime startTime,LocalDateTime endTime,
                                  String buyerPublicKey) {}
    public record DeliveryInput(String sshHost,int sshPort,String sshUsername,String note) {}
}

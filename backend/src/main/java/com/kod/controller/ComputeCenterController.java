package com.kod.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.common.Result;
import com.kod.service.ComputeCenterService;
import com.kod.service.ComputePackageService;
import com.kod.service.ComputeReferralService;
import com.kod.service.ComputeTrustService;
import com.kod.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** KOD 客户端算力中心 API。 */
@RestController
@RequestMapping("/api/compute")
@RequiredArgsConstructor
public class ComputeCenterController {

    private final ComputeCenterService computeService;
    private final ComputePackageService packageService;
    private final ComputeReferralService referralService;
    private final ComputeTrustService trustService;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    // 公开市场：未登录也可浏览。

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.ok(computeService.publicConfig());
    }

    @GetMapping("/products")
    public Result<List<Map<String, Object>>> products(
            @RequestParam(value = "type", required = false) String type) {
        return Result.ok(computeService.listProducts(type, false, null));
    }

    @GetMapping("/products/{productId}")
    public Result<Map<String, Object>> product(@PathVariable Long productId) {
        return Result.ok(computeService.getProduct(productId));
    }

    @GetMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<byte[]> productImage(@PathVariable Long productId, @PathVariable Long imageId) {
        ComputeCenterService.ProductImage image = computeService.productImage(productId, imageId);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public,max-age=86400")
                .contentType(MediaType.parseMediaType(image.mimeType())).body(image.content());
    }

    // 卡时账户与订单。

    @GetMapping("/account")
    public Result<Map<String, Object>> account(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.getAccount(userId(authorization)));
    }

    @PostMapping("/account/purchase")
    public Result<Map<String, Object>> purchase(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AmountBody body) {
        return Result.ok(computeService.purchaseCardHours(userId(authorization), body.cardHours()));
    }

    @GetMapping("/ledger")
    public Result<List<Map<String, Object>>> ledger(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.listLedger(userId(authorization)));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> orders(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.listOrders(userId(authorization)));
    }

    @PostMapping("/withdrawals")
    public Result<Map<String, Object>> withdraw(
            @RequestHeader("Authorization") String authorization,
            @RequestBody WithdrawalBody body) {
        return Result.ok(computeService.withdrawToCnyWallet(userId(authorization), body.cardHours(), body.requestId()));
    }

    @GetMapping("/withdrawals")
    public Result<List<Map<String, Object>>> withdrawals(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.listWithdrawals(userId(authorization)));
    }

    @GetMapping("/referrals/me")
    public Result<Map<String, Object>> referralProfile(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(referralService.profile(userId(authorization)));
    }

    @GetMapping("/referrals/preview")
    public Result<Map<String, Object>> referralPreview(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("code") String code) {
        return Result.ok(referralService.preview(userId(authorization), code));
    }

    @PostMapping("/referrals/bind")
    public Result<Map<String, Object>> bindReferral(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ReferralBindBody body) {
        return Result.ok(referralService.bind(userId(authorization), body.inviteCode(), body.deviceId()));
    }

    @GetMapping("/referrals/rewards")
    public Result<List<Map<String, Object>>> referralRewards(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(referralService.rewards(userId(authorization)));
    }

    // 模型 API 固定 Token 套餐。

    @PostMapping("/products/{productId}/activate")
    public Result<Map<String, Object>> activate(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId,
            @RequestParam(value = "autoTopUp", defaultValue = "false") boolean autoTopUp) {
        return Result.ok(packageService.purchase(userId(authorization), productId, autoTopUp));
    }

    @PostMapping("/products/{productId}/purchase")
    public Result<Map<String, Object>> purchasePackage(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId,
            @RequestParam(value = "autoTopUp", defaultValue = "false") boolean autoTopUp) {
        return Result.ok(packageService.purchase(userId(authorization), productId, autoTopUp));
    }

    @GetMapping("/packages/balances")
    public Result<List<Map<String, Object>>> packageBalances(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(packageService.balances(userId(authorization)));
    }

    @GetMapping("/packages/purchases")
    public Result<List<Map<String, Object>>> packagePurchases(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(packageService.purchases(userId(authorization)));
    }

    @GetMapping("/packages/purchases/{purchaseId}/credential")
    public Result<Map<String, Object>> packageCredential(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long purchaseId) {
        return Result.ok(packageService.credential(userId(authorization), purchaseId));
    }

    @PostMapping("/packages/purchases/{purchaseId}/regenerate-key")
    public Result<Map<String, Object>> regeneratePackageKey(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long purchaseId) {
        return Result.ok(packageService.regenerateKey(userId(authorization), purchaseId));
    }

    @GetMapping("/packages/authorize")
    public Result<Map<String, Object>> authorizePackage(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("modelId") String modelId) {
        return Result.ok(packageService.authorize(userId(authorization), modelId));
    }

    @GetMapping("/activations")
    public Result<List<Map<String, Object>>> activations(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.listActivations(userId(authorization)));
    }

    @GetMapping("/usage/api")
    public Result<List<Map<String, Object>>> apiUsage(@RequestHeader("Authorization") String authorization) {
        return Result.ok(packageService.usage(userId(authorization)));
    }

    // GPU 固定套餐担保交易。平台只冻结卡时、传递买家公钥和商家交付信息，不控制服务器。

    @PostMapping("/reservations")
    public Result<Map<String, Object>> reserve(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ReservationBody body,
            @RequestParam(value = "autoTopUp", defaultValue = "false") boolean autoTopUp) {
        return Result.ok(computeService.createMarketplaceOrder(userId(authorization), body.productId(),
                body.buyerPublicKey(), autoTopUp));
    }

    @GetMapping("/reservations")
    public Result<List<Map<String, Object>>> reservations(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "role", defaultValue = "buyer") String role) {
        return Result.ok(computeService.listReservations(userId(authorization), role));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public Result<Map<String, Object>> cancelReservation(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId) {
        return Result.ok(computeService.cancelReservation(userId(authorization), reservationId));
    }

    @PostMapping("/reservations/{reservationId}/delivery")
    public Result<Map<String, Object>> deliver(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId,
            @RequestBody DeliveryBody body) {
        return Result.ok(computeService.deliverMarketplaceOrder(userId(authorization), reservationId,
                body.sshHost(), body.sshPort(), body.sshUsername(), body.actualStart(), body.actualEnd(),
                body.deliveryNote()));
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public Result<Map<String, Object>> confirmDelivery(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId) {
        return Result.ok(computeService.confirmMarketplaceOrder(userId(authorization), reservationId));
    }

    @PostMapping("/reservations/{reservationId}/dispute")
    public Result<Map<String, Object>> disputeDelivery(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId,
            @RequestBody DisputeBody body) {
        return Result.ok(computeService.disputeMarketplaceOrder(userId(authorization), reservationId,
                body.reason(), body.evidence()));
    }

    // 实名认证、供应方申请、设备托管和发布。

    @GetMapping("/identity/me")
    public Result<Map<String, Object>> identityMe(@RequestHeader("Authorization") String authorization) {
        return Result.ok(trustService.identityMe(userId(authorization)));
    }

    @PostMapping(value = "/identity", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> submitIdentity(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("realName") String realName,
            @RequestPart("identityNo") String identityNo,
            @RequestPart("front") MultipartFile front,
            @RequestPart("back") MultipartFile back) {
        return Result.ok(trustService.submitIdentity(userId(authorization), realName, identityNo, front, back));
    }

    @PostMapping("/identity/test")
    public Result<Map<String, Object>> submitTestIdentity(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(trustService.submitTestIdentity(userId(authorization)));
    }

    @GetMapping("/identity/document/{side}")
    public ResponseEntity<byte[]> identityDocument(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String side) {
        byte[] content = trustService.identityDocument(userId(authorization), side);
        return privateImage(content);
    }

    @PostMapping("/identity/close")
    public Result<Map<String, Object>> closeIdentity(@RequestHeader("Authorization") String authorization) {
        trustService.closeIdentity(userId(authorization));
        return Result.ok(Map.of("closed", true, "purgeAfterDays", 30));
    }

    @GetMapping("/supplier/me")
    public Result<Map<String, Object>> supplierMe(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.supplierProfile(userId(authorization)));
    }

    @PostMapping("/supplier/apply")
    public Result<Map<String, Object>> applySupplier(
            @RequestHeader("Authorization") String authorization,
            @RequestBody SupplierBody body) {
        return Result.ok(computeService.applySupplier(userId(authorization), body.displayName(), body.contact(),
                body.description()));
    }

    @GetMapping("/supplier/products")
    public Result<List<Map<String, Object>>> supplierProducts(@RequestHeader("Authorization") String authorization) {
        Long userId = userId(authorization);
        return Result.ok(computeService.listProducts(null, true, userId));
    }

    @PostMapping("/supplier/products")
    public Result<Map<String, Object>> createSupplierProduct(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ProductBody body) {
        return Result.ok(computeService.createSupplierGpuProduct(userId(authorization), body.toInput(null)));
    }

    @PostMapping(value = "/supplier/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> addSupplierProductImage(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId,
            @RequestPart("image") MultipartFile image) {
        return Result.ok(computeService.addProductImage(userId(authorization), productId, image));
    }

    @GetMapping("/supplier/nodes")
    public Result<List<Map<String, Object>>> supplierNodes(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(trustService.myNodes(userId(authorization)));
    }

    @PostMapping(value = "/supplier/nodes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> createSupplierNode(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("payload") String payload,
            @RequestPart("resourceProof") MultipartFile resourceProof) {
        try {
            NodeBody body = objectMapper.readValue(payload, NodeBody.class);
            return Result.ok(trustService.createNode(userId(authorization), body.toInput(), resourceProof));
        } catch (com.kod.common.BizException e) {
            throw e;
        } catch (Exception e) {
            throw new com.kod.common.BizException(400, "GPU 资源信息格式不正确");
        }
    }

    // 无偿转让。

    @PostMapping("/transfers")
    public Result<Map<String, Object>> transfer(
            @RequestHeader("Authorization") String authorization,
            @RequestBody TransferBody body,
            @RequestParam(value = "autoTopUp", defaultValue = "false") boolean autoTopUp) {
        return Result.ok(computeService.createTransfer(userId(authorization), body.recipientEmail(),
                body.cardHours(), body.message(), autoTopUp));
    }

    @GetMapping("/transfers")
    public Result<List<Map<String, Object>>> transfers(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.listTransfers(userId(authorization)));
    }

    @PostMapping("/transfers/{transferId}/accept")
    public Result<Map<String, Object>> acceptTransfer(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long transferId) {
        return Result.ok(computeService.acceptTransfer(userId(authorization), transferId));
    }

    @PostMapping("/transfers/{transferId}/cancel")
    public Result<Map<String, Object>> cancelTransfer(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long transferId) {
        return Result.ok(computeService.cancelTransfer(userId(authorization), transferId));
    }

    // 客户端内通知。

    @GetMapping("/notifications")
    public Result<List<Map<String, Object>>> notifications(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.listNotifications(userId(authorization)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public Result<Map<String, Object>> markRead(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long notificationId) {
        computeService.markNotificationRead(userId(authorization), notificationId);
        return Result.ok(Map.of("read", true));
    }

    // 管理员运营接口。

    @GetMapping("/admin/overview")
    public Result<Map<String, Object>> adminOverview(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.adminOverview(userId(authorization)));
    }

    @GetMapping("/admin/upstreams")
    public Result<List<Map<String, Object>>> adminUpstreams(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(packageService.adminUpstreams(userId(authorization)));
    }

    @GetMapping("/admin/proxy-keys/suspended")
    public Result<List<Map<String, Object>>> adminSuspendedProxyKeys(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(packageService.adminSuspendedKeys(userId(authorization)));
    }

    @PostMapping("/admin/proxy-keys/{purchaseId}/repair")
    public Result<Map<String, Object>> repairProxyKey(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long purchaseId,
            @RequestBody ProxyKeyRepairBody body) {
        return Result.ok(packageService.adminRepairKey(userId(authorization), purchaseId, body.regenerate()));
    }

    @PostMapping("/admin/settings")
    public Result<Map<String, Object>> updateAdminSettings(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AdminSettingsBody body) {
        return Result.ok(computeService.updateAdminSettings(userId(authorization),
                body.transferReviewThreshold(), body.platformFeeRate()));
    }

    @GetMapping("/admin/suppliers")
    public Result<List<Map<String, Object>>> adminSuppliers(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.adminSuppliers(userId(authorization)));
    }

    @GetMapping("/admin/identities")
    public Result<List<Map<String, Object>>> adminIdentities(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(trustService.adminIdentities(userId(authorization)));
    }

    @GetMapping("/admin/identities/{identityId}")
    public Result<Map<String, Object>> adminIdentity(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long identityId) {
        return Result.ok(trustService.adminIdentityDetail(userId(authorization), identityId));
    }

    @GetMapping("/admin/identities/{identityId}/document/{side}")
    public ResponseEntity<byte[]> adminIdentityDocument(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long identityId,
            @PathVariable String side) {
        byte[] content = trustService.adminIdentityDocument(userId(authorization), identityId, side);
        return privateImage(content);
    }

    @PostMapping("/admin/identities/{identityId}/review")
    public Result<Map<String, Object>> reviewIdentity(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long identityId,
            @RequestBody ReviewBody body) {
        return Result.ok(trustService.reviewIdentity(userId(authorization), identityId,
                body.approved(), body.reason()));
    }

    @GetMapping("/admin/nodes")
    public Result<List<Map<String, Object>>> adminNodes(@RequestHeader("Authorization") String authorization) {
        return Result.ok(trustService.adminNodes(userId(authorization)));
    }

    @GetMapping("/admin/nodes/{nodeId}/proof")
    public ResponseEntity<byte[]> adminNodeProof(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long nodeId) {
        return privateImage(trustService.nodeProof(userId(authorization), nodeId));
    }

    @PostMapping("/admin/nodes/{nodeId}/review")
    public Result<Map<String, Object>> reviewNode(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long nodeId,
            @RequestBody NodeReviewBody body) {
        return Result.ok(trustService.reviewNode(userId(authorization), nodeId, body.approved(),
                body.reason(), body.verificationNote()));
    }

    @PostMapping("/admin/nodes/{nodeId}/status")
    public Result<Map<String, Object>> updateNodeStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long nodeId,
            @RequestBody NodeStatusBody body) {
        return Result.ok(trustService.updateNodeStatus(userId(authorization), nodeId, body.status(), body.reason()));
    }

    @PostMapping("/admin/suppliers/{supplierId}/review")
    public Result<Map<String, Object>> reviewSupplier(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long supplierId,
            @RequestBody ReviewBody body) {
        return Result.ok(computeService.reviewSupplier(userId(authorization), supplierId, body.approved(), body.reason()));
    }

    @GetMapping("/admin/products")
    public Result<List<Map<String, Object>>> adminProducts(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.adminProducts(userId(authorization)));
    }

    @PostMapping("/admin/products")
    public Result<Map<String, Object>> createAdminProduct(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ProductBody body) {
        Long adminUserId = userId(authorization);
        return Result.ok(computeService.createAdminApiProduct(adminUserId, body.toInput(body.supplierUserId())));
    }

    @PostMapping("/admin/products/{productId}/review")
    public Result<Map<String, Object>> reviewProduct(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId,
            @RequestBody ReviewBody body) {
        return Result.ok(computeService.reviewProduct(userId(authorization), productId, body.approved(), body.reason()));
    }

    @PostMapping("/admin/products/{productId}/upstream")
    public Result<Map<String, Object>> configureProductUpstream(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId,
            @RequestBody ProductUpstreamBody body) {
        return Result.ok(computeService.configureApiProductUpstream(
                userId(authorization), productId, body.stationId(), body.keyId()));
    }

    @GetMapping("/admin/transfers")
    public Result<List<Map<String, Object>>> adminTransfers(@RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.adminTransfers(userId(authorization)));
    }

    @PostMapping("/admin/transfers/{transferId}/review")
    public Result<Map<String, Object>> reviewTransfer(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long transferId,
            @RequestBody ReviewBody body) {
        return Result.ok(computeService.reviewTransfer(userId(authorization), transferId, body.approved(), body.reason()));
    }

    @GetMapping("/admin/reservations")
    public Result<List<Map<String, Object>>> adminReservations(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(computeService.adminReservations(userId(authorization)));
    }

    @PostMapping("/admin/reservations/{reservationId}/settle")
    public Result<Map<String, Object>> settleReservation(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId) {
        return Result.ok(computeService.settleReservationNow(userId(authorization), reservationId));
    }

    @PostMapping("/admin/reservations/{reservationId}/resolve")
    public Result<Map<String, Object>> resolveReservation(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId,
            @RequestBody ReservationResolutionBody body) {
        return Result.ok(computeService.resolveReservation(userId(authorization), reservationId,
                body.resolution(), body.actualCardHours(), body.reason()));
    }

    @PostMapping("/admin/grants")
    public Result<Map<String, Object>> grant(
            @RequestHeader("Authorization") String authorization,
            @RequestBody GrantBody body) {
        return Result.ok(computeService.grantCardHours(userId(authorization), body.recipientEmail(),
                body.cardHours(), body.expiresAt(), body.reason()));
    }

    private Long userId(String authorization) {
        return sessionService.parseUserIdFromHeader(authorization);
    }

    private ResponseEntity<byte[]> privateImage(byte[] content) {
        boolean png = content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47;
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(png ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).body(content);
    }

    public record AmountBody(BigDecimal cardHours) {
    }

    public record WithdrawalBody(BigDecimal cardHours, String requestId) {
    }

    public record ReferralBindBody(String inviteCode, String deviceId) {
    }

    public record ReservationBody(Long productId, String buyerPublicKey) {
    }

    public record DeliveryBody(String sshHost, int sshPort, String sshUsername,
                               LocalDateTime actualStart, LocalDateTime actualEnd, String deliveryNote) {
    }

    public record DisputeBody(String reason, String evidence) {
    }

    public record SupplierBody(String displayName, String contact, String description) {
    }

    public record TransferBody(String recipientEmail, BigDecimal cardHours, String message) {
    }

    public record ReviewBody(boolean approved, String reason) {
    }

    public record GrantBody(String recipientEmail, BigDecimal cardHours, LocalDateTime expiresAt, String reason) {
    }

    public record AdminSettingsBody(BigDecimal transferReviewThreshold, BigDecimal platformFeeRate) {
    }

    public record NodeReviewBody(boolean approved, String reason, String verificationNote) {
    }

    public record NodeStatusBody(String status, String reason) {
    }

    public record ReservationResolutionBody(String resolution, BigDecimal actualCardHours, String reason) {
    }

    public record ProxyKeyRepairBody(boolean regenerate) {
    }

    public record NodeBody(String nodeName, String region, String gpuModel, int gpuMemoryGb, int gpuCount,
                           String cpuDescription, int ramGb, int storageGb, String networkDescription) {
        ComputeTrustService.NodeInput toInput() {
            return new ComputeTrustService.NodeInput(nodeName, region, gpuModel, gpuMemoryGb, gpuCount,
                    cpuDescription, ramGb, storageGb, networkDescription);
        }
    }

    public record ProductBody(
            String name,
            String description,
            String region,
            String modelId,
            BigDecimal promptRatePerMillion,
            BigDecimal completionRatePerMillion,
            String gpuModel,
            Integer gpuMemoryGb,
            Integer gpuCount,
            BigDecimal pricePerGpuHour,
            LocalDateTime availableFrom,
            LocalDateTime availableTo,
            String deliveryMode,
            String slaDescription,
            Long supplierUserId,
            Long nodeId,
            Long packagePromptTokens,
            Long packageCompletionTokens,
            BigDecimal packagePriceCardHours,
            Long upstreamStationId,
            Long upstreamKeyId,
            Integer packageDurationHours,
            Integer deliveryDeadlineHours) {

        ComputeCenterService.ProductInput toInput(Long supplierUserId) {
            return new ComputeCenterService.ProductInput(name, description, region, modelId,
                    promptRatePerMillion, completionRatePerMillion, gpuModel, gpuMemoryGb, gpuCount,
                    pricePerGpuHour, availableFrom, availableTo, deliveryMode, slaDescription, supplierUserId,
                    nodeId, packagePromptTokens, packageCompletionTokens, packagePriceCardHours,
                    upstreamStationId, upstreamKeyId, packageDurationHours, deliveryDeadlineHours);
        }
    }

    public record ProductUpstreamBody(Long stationId, Long keyId) {
    }
}

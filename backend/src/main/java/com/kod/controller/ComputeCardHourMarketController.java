package com.kod.controller;

import com.kod.common.Result;
import com.kod.service.ComputeCardHourMarketService;
import com.kod.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 卡时核心业务 API；与登录邀请码、模型套餐和旧钱包表保持隔离。 */
@RestController
@RequestMapping("/api/compute/card-hours")
@RequiredArgsConstructor
public class ComputeCardHourMarketController {

    private final ComputeCardHourMarketService service;
    private final SessionService sessionService;

    @GetMapping("/market/listings")
    public Result<List<Map<String,Object>>> listings() {
        return Result.ok(service.publicListings());
    }

    @GetMapping("/market/stats")
    public Result<Map<String,Object>> stats() {
        return Result.ok(service.marketStats());
    }

    @GetMapping("/rates")
    public Result<List<Map<String,Object>>> rates() {
        return Result.ok(service.rates());
    }

    @GetMapping("/lots")
    public Result<List<Map<String,Object>>> lots(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.lots(userId(authorization)));
    }

    @GetMapping("/custody")
    public Result<Map<String,Object>> custody(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.custody(userId(authorization)));
    }

    @PostMapping("/listings")
    public Result<Map<String,Object>> createListing(@RequestHeader("Authorization") String authorization,
                                                     @RequestBody ListingBody body) {
        return Result.ok(service.createListing(userId(authorization),body.toInput()));
    }

    @GetMapping("/listings/mine")
    public Result<List<Map<String,Object>>> myListings(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.myListings(userId(authorization)));
    }

    @PostMapping("/listings/{listingId}/cancel")
    public Result<Map<String,Object>> cancelListing(@RequestHeader("Authorization") String authorization,
                                                     @PathVariable long listingId) {
        return Result.ok(service.cancelListing(userId(authorization),listingId));
    }

    @PostMapping("/listings/{listingId}/purchase-quote")
    public Result<Map<String,Object>> createPurchaseQuote(@RequestHeader("Authorization") String authorization,
                                                           @PathVariable long listingId) {
        return Result.ok(service.createPurchaseQuote(userId(authorization),listingId));
    }

    @PostMapping("/purchase-quotes/{quoteId}/confirm")
    public Result<Map<String,Object>> confirmPurchase(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable long quoteId,
                                                       @RequestParam(value="autoTopUp",defaultValue="false") boolean autoTopUp) {
        return Result.ok(service.confirmPurchaseQuote(userId(authorization),quoteId,autoTopUp));
    }

    @GetMapping("/trades")
    public Result<List<Map<String,Object>>> trades(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.trades(userId(authorization)));
    }

    @PostMapping("/rfqs")
    public Result<Map<String,Object>> createRfq(@RequestHeader("Authorization") String authorization,
                                                @RequestBody RfqBody body) {
        return Result.ok(service.createRfq(userId(authorization),body.toInput()));
    }

    @GetMapping("/rfqs")
    public Result<List<Map<String,Object>>> rfqs(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.rfqs(userId(authorization)));
    }

    @PostMapping("/rfqs/{rfqId}/quotes")
    public Result<Map<String,Object>> quoteRfq(@RequestHeader("Authorization") String authorization,
                                               @PathVariable long rfqId,@RequestBody RfqQuoteBody body) {
        return Result.ok(service.quoteRfq(userId(authorization),rfqId,body.toInput()));
    }

    @GetMapping("/rfqs/{rfqId}/quotes")
    public Result<List<Map<String,Object>>> rfqQuotes(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable long rfqId) {
        return Result.ok(service.rfqQuotes(userId(authorization),rfqId));
    }

    @PostMapping("/rfq-quotes/{quoteId}/accept")
    public Result<Map<String,Object>> acceptRfqQuote(@RequestHeader("Authorization") String authorization,
                                                      @PathVariable long quoteId,
                                                      @RequestParam(value="autoTopUp",defaultValue="false") boolean autoTopUp) {
        return Result.ok(service.acceptRfqQuote(userId(authorization),quoteId,autoTopUp));
    }

    @PostMapping("/deposits")
    public Result<Map<String,Object>> deposit(@RequestHeader("Authorization") String authorization,
                                              @RequestBody DepositBody body) {
        return Result.ok(service.createDeposit(userId(authorization),body.toInput()));
    }

    @GetMapping("/deposits")
    public Result<List<Map<String,Object>>> deposits(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.deposits(userId(authorization),false));
    }

    @PostMapping("/redemptions")
    public Result<Map<String,Object>> redeem(@RequestHeader("Authorization") String authorization,
                                             @RequestBody RedemptionBody body,
                                             @RequestParam(value="autoTopUp",defaultValue="false") boolean autoTopUp) {
        return Result.ok(service.createRedemption(userId(authorization),body.toInput(),autoTopUp));
    }

    @GetMapping("/redemptions")
    public Result<List<Map<String,Object>>> redemptions(@RequestHeader("Authorization") String authorization,
                                                        @RequestParam(value="role",defaultValue="buyer") String role) {
        return Result.ok(service.redemptions(userId(authorization),role));
    }

    @PostMapping("/redemptions/{id}/delivery")
    public Result<Map<String,Object>> deliver(@RequestHeader("Authorization") String authorization,
                                              @PathVariable long id,@RequestBody DeliveryBody body) {
        return Result.ok(service.deliverRedemption(userId(authorization),id,body.toInput()));
    }

    @PostMapping("/redemptions/{id}/usage")
    public Result<Map<String,Object>> usage(@RequestHeader("Authorization") String authorization,
                                            @PathVariable long id,@RequestBody UsageBody body) {
        return Result.ok(service.submitUsage(userId(authorization),id,body.actualGpuHours(),body.evidence()));
    }

    @PostMapping("/redemptions/{id}/top-up")
    public Result<Map<String,Object>> topUp(@RequestHeader("Authorization") String authorization,
                                            @PathVariable long id,
                                            @RequestParam(value="autoTopUp",defaultValue="false") boolean autoTopUp) {
        return Result.ok(service.topUpRedemption(userId(authorization),id,autoTopUp));
    }

    @PostMapping("/redemptions/{id}/confirm")
    public Result<Map<String,Object>> confirm(@RequestHeader("Authorization") String authorization,
                                              @PathVariable long id) {
        return Result.ok(service.confirmRedemption(userId(authorization),id));
    }

    @PostMapping("/redemptions/{id}/dispute")
    public Result<Map<String,Object>> dispute(@RequestHeader("Authorization") String authorization,
                                              @PathVariable long id,@RequestBody DisputeBody body) {
        return Result.ok(service.disputeRedemption(userId(authorization),id,body.reason()));
    }

    @GetMapping("/admin/deposits")
    public Result<List<Map<String,Object>>> adminDeposits(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.deposits(userId(authorization),true));
    }

    @PostMapping("/admin/deposits/{id}/review")
    public Result<Map<String,Object>> reviewDeposit(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable long id,@RequestBody ReviewBody body) {
        return Result.ok(service.reviewDeposit(userId(authorization),id,body.approved(),body.reason()));
    }

    @PostMapping("/admin/rates")
    public Result<Map<String,Object>> rate(@RequestHeader("Authorization") String authorization,
                                           @RequestBody RateBody body) {
        return Result.ok(service.upsertRate(userId(authorization),body.versionNo(),body.gpuModel(),
                body.multiplier(),body.notes()));
    }

    @PostMapping("/admin/redemptions/{id}/resolve")
    public Result<Map<String,Object>> resolve(@RequestHeader("Authorization") String authorization,
                                              @PathVariable long id,@RequestBody ResolutionBody body) {
        return Result.ok(service.resolveRedemption(userId(authorization),id,body.actualGpuHours(),body.reason()));
    }

    @GetMapping("/admin/redemptions")
    public Result<List<Map<String,Object>>> adminRedemptions(
            @RequestHeader("Authorization") String authorization) {
        return Result.ok(service.adminRedemptions(userId(authorization)));
    }

    private long userId(String authorization) {
        return sessionService.parseUserIdFromHeader(authorization);
    }

    public record ListingBody(String marketType,String assetType,String gpuModel,long sourceLotId,
                              BigDecimal quantity,BigDecimal unitPrice,LocalDateTime assetExpiresAt,
                              LocalDateTime listingExpiresAt,String title,String description) {
        ComputeCardHourMarketService.ListingInput toInput() {
            return new ComputeCardHourMarketService.ListingInput(marketType,assetType,gpuModel,sourceLotId,
                    quantity,unitPrice,assetExpiresAt,listingExpiresAt,title,description);
        }
    }
    public record RfqBody(String assetType,String gpuModel,BigDecimal quantity,LocalDateTime minimumExpiresAt,
                          LocalDateTime closesAt,String requirements) {
        ComputeCardHourMarketService.RfqInput toInput() {
            return new ComputeCardHourMarketService.RfqInput(assetType,gpuModel,quantity,minimumExpiresAt,closesAt,requirements);
        }
    }
    public record RfqQuoteBody(long sourceLotId,BigDecimal unitPrice) {
        ComputeCardHourMarketService.RfqQuoteInput toInput() {
            return new ComputeCardHourMarketService.RfqQuoteInput(sourceLotId,unitPrice);
        }
    }
    public record DepositBody(long nodeId,LocalDateTime availableFrom,LocalDateTime availableTo,LocalDateTime expiresAt) {
        ComputeCardHourMarketService.DepositInput toInput() {
            return new ComputeCardHourMarketService.DepositInput(nodeId,availableFrom,availableTo,expiresAt);
        }
    }
    public record RedemptionBody(long nodeId,int gpuCount,LocalDateTime startTime,LocalDateTime endTime,
                                 String buyerPublicKey) {
        ComputeCardHourMarketService.RedemptionInput toInput() {
            return new ComputeCardHourMarketService.RedemptionInput(nodeId,gpuCount,startTime,endTime,buyerPublicKey);
        }
    }
    public record DeliveryBody(String sshHost,int sshPort,String sshUsername,String note) {
        ComputeCardHourMarketService.DeliveryInput toInput() {
            return new ComputeCardHourMarketService.DeliveryInput(sshHost,sshPort,sshUsername,note);
        }
    }
    public record UsageBody(BigDecimal actualGpuHours,String evidence) {}
    public record DisputeBody(String reason) {}
    public record ReviewBody(boolean approved,String reason) {}
    public record RateBody(String versionNo,String gpuModel,BigDecimal multiplier,String notes) {}
    public record ResolutionBody(BigDecimal actualGpuHours,String reason) {}
}

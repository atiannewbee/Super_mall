package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.dto.PaymentResponse;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.PaymentService;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付查询、支付宝跳转和异步通知接口。
 */
@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/{paymentNo}")
    public PaymentResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 40) String paymentNo
    ) {
        return service.getAndRefresh(CurrentUser.id(jwt), paymentNo);
    }

    @GetMapping(value = "/alipay/{paymentNo}/launch", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> launch(
            @PathVariable @Size(max = 40) String paymentNo
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(service.launchAlipay(paymentNo));
    }

    /**
     * 支付宝公网回调入口。
     *
     * <p>该接口无需消费者 JWT，但必须通过支付宝签名、应用身份和金额校验；
     * 只有返回纯文本 success，支付宝才会停止重试。</p>
     */
    @PostMapping(
            value = "/alipay/notify",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> notify(@RequestParam Map<String, String> parameters) {
        try {
            service.handleAlipayNotification(parameters);
            return ResponseEntity.ok("success");
        } catch (ApiException exception) {
            log.warn(
                    "Rejected Alipay notification: code={}, message={}",
                    exception.getCode(),
                    exception.getMessage()
            );
            return ResponseEntity.status(exception.getStatus()).body("failure");
        } catch (RuntimeException exception) {
            log.error("Failed to process verified Alipay notification", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failure");
        }
    }
}

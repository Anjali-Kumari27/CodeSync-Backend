package com.codesync.payment.controller;

import com.codesync.payment.dto.CreateOrderRequest;
import com.codesync.payment.service.PaymentService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.codesync.payment.dto.PaymentVerifyRequest;
import com.codesync.payment.model.Payment;
import com.codesync.payment.repository.PaymentRepository;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PaymentController {

	private final PaymentService paymentService;
	private final PaymentRepository paymentRepository;

	@PostMapping("/create-order")
	public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest req) throws RazorpayException {

		return ResponseEntity.ok(paymentService.createOrder(req.getAmount()));
	}
	
	@PostMapping("/verify")
	public ResponseEntity<?> verifyPayment(
	        @RequestBody PaymentVerifyRequest request
	) {

	    Payment payment = Payment.builder()
	            .userId(request.getUserId())
	            .razorpayOrderId(request.getRazorpayOrderId())
	            .razorpayPaymentId(request.getRazorpayPaymentId())
	            .amount(request.getAmount())
	            .plan(request.getPlan())
	            .status("SUCCESS")
	            .createdAt(LocalDateTime.now())
	            .build();

	    paymentRepository.save(payment);

	    return ResponseEntity.ok("Payment Successful");
	}
}
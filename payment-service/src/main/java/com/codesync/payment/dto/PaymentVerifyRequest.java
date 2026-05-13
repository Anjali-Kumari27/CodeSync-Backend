package com.codesync.payment.dto;

import lombok.Data;

@Data
public class PaymentVerifyRequest {

	private String razorpayOrderId;

	private String razorpayPaymentId;

	private String razorpaySignature;

	private Integer amount;

	private String plan;

	private Long userId;
}
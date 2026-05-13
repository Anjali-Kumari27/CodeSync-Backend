package com.codesync.payment.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {

	private Double amount;

	private String plan;
}
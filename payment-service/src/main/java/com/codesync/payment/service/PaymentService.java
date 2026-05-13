package com.codesync.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

	@Value("${razorpay.key-id}")
	private String keyId;

	@Value("${razorpay.key-secret}")
	private String keySecret;

	public String createOrder(Double amount) throws RazorpayException {

		RazorpayClient client = new RazorpayClient(keyId, keySecret);

		JSONObject options = new JSONObject();

		options.put("amount", amount * 100);

		options.put("currency", "INR");

		options.put("receipt", "txn_" + System.currentTimeMillis());

		Order order = client.orders.create(options);

		return order.toString();
	}
}
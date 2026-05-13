package com.codesync.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    private Integer amount;

    private String plan;

    private String status;

    private LocalDateTime createdAt;
}
package Dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Transaction {
    /*
    "transactionId": "6e495cdb-514f-4eba-b022-e529c7a3866d",
    "productId": "product3",
    "productName": "headphone",
     "productCategory": "fashion",
      "productPrice": 322.54,
       "productQuantity": 8,
        "productBrand": "apple",
         "currency": "USD",
          "customerId": "rojasmatthew",
           "transactionDate": "2024-08-04T07:42:21.853789",
            "paymentMethod": "credit_card",
            "totalAmount": 2580.32
     */
    private String transactionId;
    private String productId;
    private String productName;
    private String productCategory;
    private double productPrice;
    private int productQuantity;
    private String productBrand;
    private double totalAmount;
    private String  currency;
    private String customerId;
    private Timestamp transactionDate;
    private String paymentMethod;
}

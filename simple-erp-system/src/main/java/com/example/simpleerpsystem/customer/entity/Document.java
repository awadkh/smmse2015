package com.example.simpleerpsystem.customer.entity;

import com.example.simpleerpsystem.customer.entity.enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_document")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    private Long id;

    @Column("document_type")
    private DocumentType documentType;

    @Column("file_path_or_data")
    private String filePathOrData; // Could store a file path or small textual data

    @Column("customer_id")
    private Long customerId;
}

package com.catalogstudio.wholesale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wholesale_store_categories")
@IdClass(WholesaleStoreCategory.Key.class)
public class WholesaleStoreCategory {

    @Id
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Id
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long storeId;
        private Long categoryId;
    }
}
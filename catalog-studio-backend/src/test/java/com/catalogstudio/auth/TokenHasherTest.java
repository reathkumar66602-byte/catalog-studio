package com.catalogstudio.auth;

import com.catalogstudio.common.util.TokenHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void hashesAreDeterministicAndRawKeyIsNotReversible() {
        String raw = "cst_super_secret_key";
        String hash = TokenHasher.sha256(raw);
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(TokenHasher.sha256(raw));
        assertThat(hash).doesNotContain(raw);
    }
}

package com.pulsegate.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiKeyServiceTest {

    private final ApiKeyService service = new ApiKeyService();

    @Test
    void generatesPrefixedUrlSafeKeys() {
        String key = service.generate();
        assertThat(key).startsWith("pg_").matches("pg_[A-Za-z0-9_-]{32}");
    }

    @Test
    void keysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 10_000; i++) keys.add(service.generate());
        assertThat(keys).hasSize(10_000);
    }

    @Test
    void hashIsDeterministicSha256HexAndNeverTheKey() {
        String key = service.generate();
        String hash = service.hash(key);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo(key).doesNotContain(key);
        assertThat(service.hash(key)).isEqualTo(hash);
        assertThat(service.hash(key + "x")).isNotEqualTo(hash);
    }

    @Test
    void knownVector() {
        // echo -n "abc" | sha256sum
        assertThat(service.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void displayPrefixIsFirstEightCharacters() {
        assertThat(service.displayPrefix("pg_abcdefghijk")).isEqualTo("pg_abcde");
    }
}

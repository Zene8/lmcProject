package com.lmc.ide;

import java.util.concurrent.CompletableFuture;

public interface InputProvider {
    CompletableFuture<Integer> requestInput();

    
}

package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared execution pipeline for SofaScore providers.
 * Browser ownership and lifecycle are delegated to the singleton session manager.
 */
@Slf4j
public abstract class AbstractSofaScoreProvider {
    @Autowired
    private SofaScoreBrowserSessionManager browserSessionManager;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    protected final <T> T withPage(Function<Page, T> action) {
        Page page = null;
        try {
            page = createPage();
            return action.apply(page);
        } finally {
            closePage(page);
        }
    }

    public Page createPage() {
        return browserSessionManager.openPage();
    }

    protected void closePage(Page page) {
        browserSessionManager.closePage(page);
    }

    @PreDestroy
    public void shutdown() {
        // The shared manager owns shutdown; individual provider beans must not close it.
    }

    protected <D> void executeSimpleStorePipeline(Function<Page, D> fetchFunction, Consumer<D> saveConsumer) {
        Page page = null;
        try {
            page = createPage();
            D dtoResult = fetchFunction.apply(page);
            if (dtoResult != null) {
                saveConsumer.accept(dtoResult);
            }
        } catch (SofaScoreFetchException exception) {
            throw exception;
        } catch (PlaywrightException exception) {
            throw new SofaScoreFetchException("SofaScore browser operation failed", exception);
        } catch (Exception exception) {
            throw new SofaScoreFetchException("SofaScore data processing failed", exception);
        } finally {
            closePage(page);
        }
    }

    protected <E, D> void executeStorePipeline(List<E> entities,
                                                BiFunction<E, Page, D> fetchFunction,
                                                BiConsumer<E, D> saveConsumer) {
        if (CollectionUtils.isEmpty(entities)) {
            return;
        }

        Page page = null;
        try {
            page = createPage();
            for (E entity : entities) {
                int attempts = 0;
                boolean success = false;
                while (attempts < MAX_RETRY_ATTEMPTS && !success) {
                    attempts++;
                    if (page == null || page.isClosed()) {
                        page = createPage();
                    }
                    try {
                        D dtoResult = fetchFunction.apply(entity, page);
                        if (dtoResult != null) {
                            saveConsumer.accept(entity, dtoResult);
                        }
                        success = true;
                    } catch (PlaywrightException exception) {
                        log.warn("[{}] Browser operation failed for item {} (attempt {}/{}): {}",
                                Provider.SOFA_SCORE.name(), entity, attempts, MAX_RETRY_ATTEMPTS,
                                exception.getMessage());
                        closePage(page);
                        page = null;
                        if (attempts < MAX_RETRY_ATTEMPTS) {
                            backoffDelay(attempts);
                        }
                    } catch (SofaScoreFetchException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new SofaScoreFetchException(
                                "SofaScore processing failed for item " + entity, exception);
                    }
                }
                if (!success) {
                    throw new SofaScoreFetchException(
                            "SofaScore processing exhausted retries for item " + entity);
                }
            }
        } finally {
            closePage(page);
        }
    }

    protected <D> D executeSimpleFetchPipeline(Function<Page, D> fetchFunction) {
        Page page = null;
        try {
            page = createPage();
            return fetchFunction.apply(page);
        } catch (SofaScoreFetchException exception) {
            throw exception;
        } catch (PlaywrightException exception) {
            throw new SofaScoreFetchException("SofaScore browser operation failed", exception);
        } catch (Exception exception) {
            throw new SofaScoreFetchException("SofaScore data retrieval failed", exception);
        } finally {
            closePage(page);
        }
    }

    private void backoffDelay(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SofaScoreFetchException("Interrupted while retrying SofaScore request", exception);
        }
    }
}

package io.antigen.core.interceptor;

public class TestContextManager {
    private static final ThreadLocal<TestContext> contextHolder = new ThreadLocal<>();

    private TestContextManager() {
    }

    public static void setContext(TestContext context) {
        contextHolder.set(context);
    }

    public static TestContext getContext() {
        TestContext context = contextHolder.get();
        if (context == null) {
            throw new IllegalStateException("TestContext is not initialized for the current thread. " +
                    "Ensure the test execution is wrapped by the Antigen aspect.");
        }
        return context;
    }

    /**
     * Checks if a TestContext exists for the current thread without throwing an exception.
     * Useful for defensive checks in HTTP interceptors that may be called outside of @Test methods.
     *
     * @return true if a context exists, false otherwise
     */
    public static boolean hasContext() {
        return contextHolder.get() != null;
    }

    public static void clearContext() {
        contextHolder.remove();
    }
}

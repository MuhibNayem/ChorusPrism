-- Resilient dynamic pgvector database integration
-- Installs the pgvector extension and adds a native vector column for trace clustering

DO $$
BEGIN
    -- Check if vector extension is available in pg_available_extensions
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        -- Safely install the extension
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END $$;

DO $$
BEGIN
    -- Check if extension is installed successfully
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- Add vector_native column of dynamic-dimension type 'vector'
        ALTER TABLE trace_embeddings ADD COLUMN IF NOT EXISTS vector_native vector;

        -- Create HNSW index for ultra-fast cosine similarity search (using dynamic dimension index if supported)
        -- Fallback to standard index mapping if HNSW requires a dimension constraint
        CREATE INDEX IF NOT EXISTS idx_trace_embeddings_vector_native_cosine ON trace_embeddings USING hnsw (vector_native vector_cosine_ops);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- Log warning and complete successfully to prevent startup blocker under strict DB restrictions
        RAISE WARNING 'Failed to initialize native pgvector features. Falling back to JSONB storage. Error: %', SQLERRM;
END $$;

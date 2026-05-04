-- Switch git_commits primary key from IDENTITY (per-row roundtrip) to a sequence
-- with increment 500, matching Hibernate's allocationSize. This lets Hibernate
-- reserve 500 IDs per sequence fetch and batch-insert entire saveAll() calls
-- as a single JDBC batch instead of 500 individual INSERT+SELECT roundtrips.
ALTER SEQUENCE git_commits_id_seq INCREMENT BY 500;

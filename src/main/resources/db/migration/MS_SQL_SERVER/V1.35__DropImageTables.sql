-- Drop mcp_image table
IF EXISTS (SELECT * FROM sys.tables WHERE NAME = 'mcp_image')
BEGIN
    DROP TABLE mcp_image;
END
GO

-- Drop built_image table
IF EXISTS (SELECT * FROM sys.tables WHERE NAME = 'built_image')
BEGIN
    DROP TABLE built_image;
END
GO

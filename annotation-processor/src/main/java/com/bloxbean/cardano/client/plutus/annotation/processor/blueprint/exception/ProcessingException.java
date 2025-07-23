package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.exception;

/**
 * Exception thrown during Blueprint schema processing.
 * Provides context about what went wrong during schema analysis and processing.
 */
public class ProcessingException extends RuntimeException {
    
    private final String schemaTitle;
    private final String dataType;
    
    public ProcessingException(String message) {
        super(message);
        this.schemaTitle = null;
        this.dataType = null;
    }
    
    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.schemaTitle = null;
        this.dataType = null;
    }
    
    public ProcessingException(String message, String schemaTitle, String dataType) {
        super(message);
        this.schemaTitle = schemaTitle;
        this.dataType = dataType;
    }
    
    public ProcessingException(String message, String schemaTitle, String dataType, Throwable cause) {
        super(message, cause);
        this.schemaTitle = schemaTitle;
        this.dataType = dataType;
    }
    
    /**
     * Returns the schema title that was being processed when the error occurred
     * 
     * @return schema title or null
     */
    public String getSchemaTitle() {
        return schemaTitle;
    }
    
    /**
     * Returns the data type that was being processed when the error occurred
     * 
     * @return data type or null
     */
    public String getDataType() {
        return dataType;
    }
    
    /**
     * Returns a detailed error message with context
     * 
     * @return detailed error message
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        
        if (schemaTitle != null) {
            sb.append("Schema '").append(schemaTitle).append("': ");
        }
        
        if (dataType != null) {
            sb.append("DataType '").append(dataType).append("': ");
        }
        
        sb.append(getMessage());
        
        if (getCause() != null) {
            sb.append(" - Caused by: ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "ProcessingException{" +
               "schemaTitle='" + schemaTitle + '\'' +
               ", dataType='" + dataType + '\'' +
               ", message='" + getMessage() + '\'' +
               '}';
    }
}
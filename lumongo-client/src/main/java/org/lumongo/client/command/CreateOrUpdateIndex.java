package org.lumongo.client.command;

import org.lumongo.client.pool.LumongoConnection;
import org.lumongo.client.result.CreateIndexResult;
import org.lumongo.client.result.CreateOrUpdateIndexResult;
import org.lumongo.client.result.GetIndexesResult;
import org.lumongo.client.result.UpdateIndexResult;
import org.lumongo.cluster.message.Lumongo.IndexSettings;

import com.google.protobuf.ServiceException;

/**
 * Creates a new index with all settings given or updates the IndexSettings on an existing index
 * @author mdavis
 *
 */
public class CreateOrUpdateIndex extends Command<CreateOrUpdateIndexResult> {
    private String indexName;
    private Integer numberOfSegments;
    private String uniqueIdField;
    private Boolean faceted;
    private IndexSettings indexSettings;

    public CreateOrUpdateIndex(String indexName, Integer numberOfSegments, String uniqueIdField, IndexSettings indexSettings) {
        this.indexName = indexName;
        this.numberOfSegments = numberOfSegments;
        this.uniqueIdField = uniqueIdField;
        this.indexSettings = indexSettings;
    }

    public CreateOrUpdateIndex setFaceted(Boolean faceted) {
        this.faceted = faceted;
        return this;
    }

    public Boolean getFaceted() {
        return faceted;
    }

    @Override
    public CreateOrUpdateIndexResult execute(LumongoConnection lumongoConnection) throws ServiceException {
        CreateOrUpdateIndexResult result = new CreateOrUpdateIndexResult();

        GetIndexes gt = new GetIndexes();
        GetIndexesResult gtr = gt.execute(lumongoConnection);
        if (gtr.containsIndex(indexName)) {
            UpdateIndex ui = new UpdateIndex(indexSettings);
            UpdateIndexResult uir = ui.execute(lumongoConnection);
            result.setUpdateIndexResult(uir);
            return result;
        }

        CreateIndex ci = new CreateIndex(indexName, numberOfSegments, uniqueIdField, indexSettings);
        CreateIndexResult cir = ci.execute(lumongoConnection);
        result.setCreateIndexResult(cir);
        return result;
    }



}

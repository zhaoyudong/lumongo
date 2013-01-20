package org.lumongo.client.command;

import org.lumongo.client.command.base.SimpleCommand;
import org.lumongo.client.pool.LumongoConnection;
import org.lumongo.client.result.GetNumberOfDocsResult;
import org.lumongo.cluster.message.Lumongo.ExternalService;
import org.lumongo.cluster.message.Lumongo.GetNumberOfDocsRequest;
import org.lumongo.cluster.message.Lumongo.GetNumberOfDocsResponse;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;

public class GetNumberOfDocs extends SimpleCommand<GetNumberOfDocsRequest, GetNumberOfDocsResult> {

	private String indexName;
	private Boolean realTime;

	public GetNumberOfDocs(String indexName) {
		this.indexName = indexName;
	}

	public Boolean getRealTime() {
		return realTime;
	}

	public GetNumberOfDocs setRealTime(Boolean realTime) {
		this.realTime = realTime;
		return this;
	}

	@Override
	public GetNumberOfDocsRequest getRequest() {
		return GetNumberOfDocsRequest.newBuilder().setIndexName(indexName).build();
	}

	@Override
	public GetNumberOfDocsResult execute(LumongoConnection lumongoConnection) throws ServiceException {
		ExternalService.BlockingInterface service = lumongoConnection.getService();

		RpcController controller = lumongoConnection.getController();

		GetNumberOfDocsResponse getNumberOfDocsResponse = service.getNumberOfDocs(controller, getRequest());

		return new GetNumberOfDocsResult(getNumberOfDocsResponse);
	}



}

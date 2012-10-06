package org.lumongo.client.command;

import org.lumongo.client.pool.LumongoConnection;
import org.lumongo.client.result.FetchResult;
import org.lumongo.cluster.message.Lumongo.ExternalService;
import org.lumongo.cluster.message.Lumongo.FetchRequest;
import org.lumongo.cluster.message.Lumongo.FetchRequest.FetchType;
import org.lumongo.cluster.message.Lumongo.FetchResponse;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;

public class Fetch extends Command<FetchRequest, FetchResult> {

	private String uniqueId;
	private String fileName;
	private FetchType resultFetchType;
	private FetchType associatedFetchType;

	public Fetch(String uniqueId) {
		this.uniqueId = uniqueId;
	}

	public String getUniqueId() {
		return uniqueId;
	}

	public Fetch setFileName(String fileName) {
		this.fileName = fileName;
		return this;
	}

	public String getFileFame() {
		return fileName;
	}

	public FetchType getResultFetchType() {
		return resultFetchType;
	}

	public Fetch setResultFetchType(FetchType resultFetchType) {
		this.resultFetchType = resultFetchType;
		return this;
	}

	public FetchType getAssociatedFetchType() {
		return associatedFetchType;
	}

	public Fetch setAssociatedFetchType(FetchType associatedFetchType) {
		this.associatedFetchType = associatedFetchType;
		return this;
	}

	@Override
	public FetchRequest getRequest() {
		FetchRequest.Builder fetchRequestBuilder = FetchRequest.newBuilder();
		if (uniqueId != null) {
			fetchRequestBuilder.setUniqueId(uniqueId);
		}
		if (fileName != null) {
			fetchRequestBuilder.setFileName(fileName);
		}
		if (resultFetchType != null) {
			fetchRequestBuilder.setResultFetchType(FetchType.NONE);
		}
		if (associatedFetchType != null) {
			fetchRequestBuilder.setAssociatedFetchType(FetchType.FULL);
		}
		return fetchRequestBuilder.build();
	}

	@Override
	public FetchResult execute(LumongoConnection lumongoConnection) throws ServiceException {

		ExternalService.BlockingInterface service = lumongoConnection.getService();

		RpcController controller = lumongoConnection.getController();

		FetchResponse fetchResponse = service.fetch(controller, getRequest());

		return new FetchResult(fetchResponse);

	}



}

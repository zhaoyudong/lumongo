package org.lumongo.admin;

import java.io.File;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.lumongo.LumongoConstants;
import org.lumongo.admin.help.LumongoHelpFormatter;
import org.lumongo.client.LumongoRestClient;
import org.lumongo.util.LogUtil;

public class StoreAssociated {

	public static void main(String[] args) throws Exception {
		LogUtil.loadLogConfig();

		OptionParser parser = new OptionParser();
		OptionSpec<String> addressArg = parser.accepts(AdminConstants.ADDRESS).withRequiredArg().defaultsTo("localhost").describedAs("Lumongo server address");
		OptionSpec<Integer> restPortArg = parser.accepts(AdminConstants.REST_PORT).withRequiredArg().ofType(Integer.class)
				.defaultsTo(LumongoConstants.DEFAULT_REST_SERVICE_PORT).describedAs("Lumongo rest port");
		OptionSpec<String> uniqueIdArg = parser.accepts(AdminConstants.UNIQUE_ID).withRequiredArg().required().describedAs("Unique Id");
		OptionSpec<String> indexArg = parser.accepts(AdminConstants.INDEX).withRequiredArg().required().describedAs("Index");
		OptionSpec<String> fileNameArg = parser.accepts(AdminConstants.FILE_NAME).withRequiredArg().required().describedAs("Associated File Name");
		OptionSpec<File> fileToStoreArg = parser.accepts(AdminConstants.FILE_TO_STORE).withRequiredArg().ofType(File.class).required()
				.describedAs("Associated File to Store");

		try {
			OptionSet options = parser.parse(args);

			String address = options.valueOf(addressArg);
			int restPort = options.valueOf(restPortArg);
			String uniqueId = options.valueOf(uniqueIdArg);
			String indexName = options.valueOf(indexArg);
			String fileName = options.valueOf(fileNameArg);
			File fileToStore = options.valueOf(fileToStoreArg);

			LumongoRestClient client = new LumongoRestClient(address, restPort);
			client.storeAssociated(uniqueId, indexName, fileName, fileToStore);

		}
		catch (OptionException e) {
			System.err.println("ERROR: " + e.getMessage());
			parser.formatHelpWith(new LumongoHelpFormatter());
			parser.printHelpOn(System.out);
		}
	}
}

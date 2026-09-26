package raidqueue;

import net.fabricmc.api.ClientModInitializer;
import raidqueue.ui.ClientViewNetworking;

public class RaidDenQueueClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientViewNetworking.init();
	}
}
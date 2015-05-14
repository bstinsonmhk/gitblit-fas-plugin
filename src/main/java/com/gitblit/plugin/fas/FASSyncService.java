package com.gitblit.plugin.fas;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.plugin.fas.FASAuthProvider;

public final class FASSyncService implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(FASSyncService.class);

	private final IStoredSettings settings;

	private final FASAuthProvider fasAuthProvider;

	private final AtomicBoolean running = new AtomicBoolean(false);
	
	public FASSyncService(IStoredSettings settings, FASAuthProvider fasAuthProvider) {
		this.settings = settings;
		this.fasAuthProvider = fasAuthProvider;
	}

	@Override
	public void run() {
		logger.info("Starting user and group sync with FAS");
		
		if (!running.getAndSet(true)) {
			try {
				fasAuthProvider.sync();
			} catch (Exception e) {
				logger.error("Failed to synchronize with FAS", e);
			} finally {
				running.getAndSet(false);
			}
		}
		
		logger.info("Synchronization Finished");		
	}
	
	public boolean isReady(){
		return true; //TODO: Put this in settings
	}
}

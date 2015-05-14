package com.gitblit.plugin.fas;

import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.extensions.GitblitPlugin;

public class Plugin extends GitblitPlugin {

	public static final String SETTING_FAS_URL = "fas.url";
	public static final String SETTING_FAS_USERNAME = "fas.username";
	public static final String SETTING_FAS_PASSWORD = "fas.password";
	public static final String SETTING_FAS_SYNC_ENABLED = "fas.syncEnabled";
	public static final String SETTING_FAS_DELETE_REMOVED_USERS = "fas.deleteRemovedUsers";
	public static final String SETTING_FAS_DELETE_REMOVED_TEAMS = "fas.deleteRemovedTeams";

	public Plugin(PluginWrapper wrapper) {
		super(wrapper);

	}

	@Override
	public void start() {
		log.debug("{} STARTED.", getWrapper().getPluginId());
	}

	@Override
	public void stop() {
		log.debug("{} STOPPED.", getWrapper().getPluginId());
	}

	@Override
	public void onInstall() {
		log.debug("{} INSTALLED.", getWrapper().getPluginId());
	}

	@Override
	public void onUpgrade(Version oldVersion) {
		log.debug("{} UPGRADED from {}.", getWrapper().getPluginId(), oldVersion);
	}

	@Override
	public void onUninstall() {
		log.debug("{} UNINSTALLED.", getWrapper().getPluginId());
	}
}

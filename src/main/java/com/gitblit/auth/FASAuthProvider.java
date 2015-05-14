package com.gitblit.auth;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.service.FASSyncService;

import org.centos.jFAS2.FAS2Client;
import org.centos.jFAS2.FAS2Client.FASPerson;
import org.centos.jFAS2.FAS2Client.FASGroup;


public class FASAuthProvider extends UsernamePasswordAuthenticationProvider {
	
	private final ScheduledExecutorService scheduledExecutorService;

    public FASAuthProvider() {
        super("fas");
        
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void setup() {
    	configureSyncService();
    }
    
    @Override
    public void stop(){
    	scheduledExecutorService.shutdownNow();
    }
    
    public void sync(){
    	final boolean enabled = settings.getBoolean(Plugin.SETTING_FAS_SYNC_ENABLED, false); // TODO: Get this from the config
    	final boolean deleteRemovedUsers = settings.getBoolean(Plugin.SETTING_FAS_DELETE_REMOVED_USERS, true);
    	final boolean deleteRemovedTeams = settings.getBoolean(Plugin.SETTING_FAS_DELETE_REMOVED_TEAMS, true);
    	
    	if (enabled){
            final String fasURL = settings.getRequiredString(Plugin.SETTING_FAS_URL);
            final String fasUsername = settings.getRequiredString(Plugin.SETTING_FAS_USERNAME);
            final String fasPassword = settings.getRequiredString(Plugin.SETTING_FAS_PASSWORD);

    		logger.info("sync(): Synchronizing with FAS");
    		FAS2Client fas2client = new FAS2Client(fasURL, fasUsername, fasPassword);
    		Map<Integer, FASPerson> people = fas2client.getPeople();
    		Map<Integer, FASGroup> groups = fas2client.getGroups();
    		
    		// Sync all Users
    		if (people != null && people.size() > 0 ) {
    			final Map<String, UserModel> fasUsers = new HashMap<String,UserModel>(); 
        		for(Map.Entry<Integer, FASPerson> fp : people.entrySet()){
        			FASPerson fu = fp.getValue();
        			
        			if (fu.username == null) {
        				logger.error("Could not get userid from FAS");
        				continue;
        			}
        			logger.debug("FAS synchronizing: " + fu.username);
        			
        			UserModel gbuser = userManager.getUserModel(fu.username);
        			
        			if (gbuser == null){
        				gbuser = new UserModel(fu.username);
        			} else if (gbuser.isLocalAccount()){
        				logger.debug("Not syncing account {} because it is a Local account", gbuser.username);
        				continue;
        			}      			
        			setUserAttributes(gbuser, fu);
        			gbuser.teams.clear();
        			fasUsers.put(fu.username, gbuser);
        		}
        		
        		if ( deleteRemovedUsers ){
        			for(UserModel userModel : userManager.getAllUsers()){
        				if (AccountType.EXTERNAL == userModel.accountType){
        					if (!fasUsers.containsKey(userModel.username)){
        						logger.info("Deleting removed user {}", userModel.username);
        						userManager.deleteUser(userModel.username);
        					}
        				}
        			}
        		}
        		userManager.updateUserModels(fasUsers.values());
    		}
    		
    		if( groups != null && groups.size() > 0 ){
    			final Map<String, TeamModel> fasTeams = new HashMap<String, TeamModel>();
    			
    			for(Map.Entry<Integer, FASGroup> groupentry : groups.entrySet()){
    				FASGroup fg = groupentry.getValue();
    				
    				if ( fg.name == null ){
    					logger.error("Could not get team id from FAS");
    					continue;
    				}
    				
    				logger.debug("FAS Synchronizing team: {}", fg.name);
    				
    				TeamModel gbteam = userManager.getTeamModel(fg.name);
    				
    				if (gbteam == null){
    					gbteam = new TeamModel(fg.name);
    					gbteam.accountType = getAccountType();
    				}
    				
    				if( fg.members != null && fg.members.size() > 0 ){
    					logger.debug("Group: {}\n{}", fg.name, fg.members);
    					for(FASPerson member : fg.members){
    						UserModel um = userManager.getUserModel(member.username);

    						if( um == null ){
    							logger.error("User {} does not exist!", member.username);
    						}

    						logger.info("Adding user {} to team {}", member.username, gbteam.name);
    						um.teams.add(gbteam);
    						gbteam.addUser(um.getName());
    						fasTeams.put(fg.name, gbteam);
    					}
    				} else {
    					logger.debug("Adding empty group: {}", gbteam.name);
    					fasTeams.put(fg.name, gbteam);
    				}
    			}

    			if ( deleteRemovedTeams ){
    				for(TeamModel teamModel : userManager.getAllTeams()){
    					if ( AccountType.EXTERNAL == teamModel.accountType ){
    						if ( !fasTeams.containsKey(teamModel.name) ){
    							logger.info("Deleting removed Team: {}", teamModel.name);
    							userManager.deleteTeam(teamModel.name);
    						}
    					}
    				}
    			}
    			userManager.updateTeamModels(fasTeams.values());
    		}
    	}
    }

    @Override
    public boolean supportsCredentialChanges() {
        return false;
    }

    @Override
    public boolean supportsDisplayNameChanges() {
        return false;
    }

    @Override
    public boolean supportsEmailAddressChanges() {
        return false;
    }

    @Override
    public boolean supportsTeamMembershipChanges() {
        return false;
    }

	 @Override
	public AccountType getAccountType() {
		return AccountType.EXTERNAL;
	}

    @Override
    public UserModel authenticate(String username, char[] password) {
		return null;
       
    }
    
    private void setUserAttributes(UserModel user, FASPerson fp){
    	user.password = Constants.EXTERNAL_ACCOUNT;
    	user.accountType = getAccountType();
    	
    	if(!StringUtils.isEmpty(fp.display_name)){
    		user.displayName = fp.display_name;
    	} else {
    		user.displayName = fp.username;
    	}
    	
    	if(!StringUtils.isEmpty(fp.email)){
    		user.emailAddress = fp.email;
    	} else {
    		user.emailAddress = null;
    	}

    }
    
    private void configureSyncService(){
    	FASSyncService fasSyncService = new FASSyncService(settings, this);
    	
    	if (fasSyncService.isReady()){
    		long fasSyncPeriod = 60000; // milliseconds
    		int delay = 1;
    		logger.info("Gitblit will sync from FAS every minute");
    		scheduledExecutorService.scheduleAtFixedRate(fasSyncService, delay, fasSyncPeriod, TimeUnit.MILLISECONDS);
    	} else {
    		logger.info("FAS Sync is disabled");
    	}
    }

}

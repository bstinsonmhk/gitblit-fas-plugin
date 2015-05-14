package org.centos.jFAS2;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.gitblit.utils.ConnectionUtils;
import com.google.gson.Gson;


public class FAS2Client {
	private String FAS2username;
	private String FAS2password;
    private String topurl;
	private Map <Integer, FASPerson> people;
	private Map <Integer, FASGroup> groups;
	
	public FAS2Client(String topurl, String username, String password){
		this.people = new HashMap<Integer, FASPerson>();
		this.groups = new HashMap<Integer, FASGroup>();
        
        this.topurl = topurl;
		this.FAS2username = username;
		this.FAS2password = password;
		
		this.getPeople();
		this.getGroups();
	}
	
	public Map<Integer, FASGroup> getGroups(){
		String groupjson = "";
		
		if (this.groups == null || this.groups.isEmpty()){
			try {
				groupjson = send_request("group");
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			FasGroupResponse groups = new Gson().fromJson(groupjson, FasGroupResponse.class);
			
			for (FASGroup g : groups.groups){
				FASGroup fullgroup = get_individual_group(g.name);
				if (fullgroup.members == null){
					fullgroup.members = new ArrayList<FASPerson>();
				}
				for(GroupRole role: fullgroup.approved_roles){
					FASPerson fp = this.people.get(role.person_id);
					if(fp != null && !fp.status.equals("inactive")){
						fullgroup.members.add(fp);
					}

				}
				this.groups.put(fullgroup.id, fullgroup);
			}
		}
		return this.groups;
	}
	
	public Map<Integer, FASPerson> getPeople(){
		String userjson="";
		
		if (this.people == null || this.people.isEmpty()) {
			try {
				userjson = send_request("user");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			FasPersonResponse users = new Gson().fromJson(userjson, FasPersonResponse.class);
	
			for (FASPerson p : users.unapproved_people){
				
				if( !p.status.equals("inactive") ){
					people.put(p.id, p);
				}
			}
		}
		return people;
	}
	
	private class TGResponse{
		String tg_flash;
		Boolean success;
	}
	
	public class FASPerson{
		public String username;
		public String email;
		public String role_status;
		public Integer id;
		public String display_name;
		public String status;
		
	}
	
	public class FASGroup{
		public String display_name;
		public String name;
		public Boolean needs_sponsor;
		public Integer id;
		public Integer owner_id;
		public GroupRole[] approved_roles;
		
		public List<FASPerson> members;
		
		public void loadmembers(){
			FASGroup thisgroup = get_individual_group(this.name);
			
			if (this.members == null) {
				this.members = new LinkedList<FASPerson>();
			}
			
			for(GroupRole role : thisgroup.approved_roles){
				FASPerson person = get_individual_person(role.person_id);
				
				if (!person.status.equals("inactive")){
					this.members.add(person);
				}
			}
		}
	}
	
	private class GroupRole {
		String role_status;
		Integer sponsor_id;
		Integer person_id;
	}
	
	private class IndividualGroupResponse extends TGResponse{
		FASGroup group;
	}
	
	private class IndividualPersonResponse extends TGResponse{
		FASPerson person;
	}
	
	private class FasGroupResponse extends TGResponse{
		String search;
		
		FASGroup[] groups;
	}
	private class FasPersonResponse extends TGResponse{
		String search;
		
		FASPerson[] unapproved_people;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		FAS2Client f = new FAS2Client("http://fas1.centos.lan","admin","admin");
		
		for (Map.Entry<Integer, FASPerson> fu : f.getPeople().entrySet()){
			System.out.println(fu.getValue().username);
		}
		
		f.getPeople();
		
		for (Map.Entry<Integer, FASGroup> fg: f.getGroups().entrySet()){
			FASGroup g = fg.getValue();
			System.out.println(String.format("===== Members of: %s =======", g.name));
			for (FASPerson p : g.members){
				System.out.println(p.username);
			}
		}

	}
	
	private FASGroup get_individual_group(String groupname){
		try {
			return new Gson().fromJson(this.send_json_byname_request("group", groupname), IndividualGroupResponse.class).group;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private FASPerson get_individual_person(Integer personid){
		try {
			return new Gson().fromJson(this.send_json_byid_request("person", personid), IndividualPersonResponse.class).person;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("OH NO RETURNING NULL!");
		return null;
	}
	
	private String send_json_byid_request(String entitytype, Integer entityid) throws IOException {

		String url = String.format("%s/json/%s_by_id", this.topurl, entitytype);
        String poststring = String.format("%s_id=%s&login=Login&password=%s&user_name=%s", entitytype, entityid, this.FAS2username, this.FAS2password);
        byte[] postData = poststring.getBytes( Charset.forName("UTF-8") );
        
        HttpURLConnection http;
        http = (HttpURLConnection) ConnectionUtils.openConnection(url, null, null);
        http.setRequestProperty("Accept","application/json");
        //http.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");
        http.setRequestMethod("GET");
        http.connect();
        
        try(DataOutputStream poststream = new DataOutputStream(http.getOutputStream())){
        	poststream.write(postData);
        }

        InputStreamReader reader = new InputStreamReader(http.getInputStream());
        String outputstring = IOUtils.toString(reader);
        
        reader.close();
        return outputstring;
		
	}
	
	private String send_json_byname_request(String entitytype, String entityname) throws IOException {

		String url = String.format("%s/json/%s_by_name", this.topurl, entitytype);
        String poststring = String.format("%sname=%s&login=Login&password=%s&user_name=%s", entitytype, entityname, this.FAS2username, this.FAS2password);
        byte[] postData = poststring.getBytes( Charset.forName("UTF-8") );
        
        HttpURLConnection http;
        http = (HttpURLConnection) ConnectionUtils.openConnection(url, null, null);
        http.setRequestProperty("Accept","application/json");
        //http.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");
        http.setRequestMethod("GET");
        http.connect();
        
        try(DataOutputStream poststream = new DataOutputStream(http.getOutputStream())){
        	poststream.write(postData);
        }

        InputStreamReader reader = new InputStreamReader(http.getInputStream());
        String outputstring = IOUtils.toString(reader);
        
        reader.close();
        return outputstring;
	}

    private String send_request(String facility) throws IOException {
    	
        String url = String.format("%s/%s/list", this.topurl, facility);
    	//String url = "http://localhost:8080";
        String poststring = String.format("search=*&login=Login&password=%s&user_name=%s", this.FAS2username, this.FAS2password);
        byte[] postData = poststring.getBytes( Charset.forName("UTF-8") );
        
        HttpURLConnection http;
        http = (HttpURLConnection) ConnectionUtils.openConnection(url, null, null);
        http.setRequestProperty("Accept","application/json");
        //http.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");
        http.setRequestMethod("GET");
        http.connect();
        
        try(DataOutputStream poststream = new DataOutputStream(http.getOutputStream())){
        	poststream.write(postData);
        }

        InputStreamReader reader = new InputStreamReader(http.getInputStream());
        String outputstring = IOUtils.toString(reader);
        
        reader.close();
        return outputstring;
    }
}

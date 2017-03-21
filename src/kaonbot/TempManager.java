package kaonbot;
import java.util.ArrayList;
import java.util.List;

import bwapi.Unit;

public abstract class TempManager implements UnitCommander{
	
	private List<Claim> claims;
	private boolean done = false;
	
	public TempManager(){
		claims = new ArrayList<Claim>();
	}
	
	public TempManager(Claim c){
		this();
		claims.add(c);
	}
	
	public TempManager(List<Claim> claims){
		this.claims = claims;
	}
	
	@Override public String getName(){
		return this.toString();
	}
	
	public abstract void runFrame();
	protected void setDone(){
		done = true;
	}
	public boolean isDone(){
		return done;
	}
	
	public void cancel(){
		freeUnits();
		setDone();
	}
	
	@Override
	public List<Claim> getAllClaims(){
		return claims;
	}
	
	public void freeUnits(){
		for(Claim c: claims){
			c.free();
		}
		claims.clear();
	}
}

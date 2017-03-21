package kaonbot;
import java.util.ArrayList;
import java.util.List;

import bwapi.Unit;

public class Claim implements Comparable<Claim>{
	private UnitCommander commander;
	private double priority;
	public final Unit unit;
	private List<Runnable> onCommandeer = new ArrayList<Runnable>();
	private int claimFrame;
	private static final int CLAIM_LOCK = 50;
	private int lastTouched;
	
	public Claim(UnitCommander man, double cl, Unit u)
	{
		commander = man;
		priority = cl;
		unit = u;
		claimFrame = KaonBot.getGame().getFrameCount();
		lastTouched = claimFrame;
	}

	public String toString(){
		try {
			return unit.getID() + " " + unit.getType() + " - " + commander.getName() + " - " + onCommandeer.size();
		} catch (Exception e) {
			KaonBot.print(unit + "", true);
			KaonBot.print(commander + "", true);
			KaonBot.print(onCommandeer + "", true);
		}
		return "BUGGED CLAIM toString()";
	}
	
	public void touch(){
		lastTouched = KaonBot.getGame().getFrameCount();
	}
	
	public int getLastTouched(){
		return lastTouched;
	}
	
	public void addOnCommandeer(Runnable r){
		onCommandeer.add(r);
	}
	
	public void free(){
		commandeer(null, Double.MAX_VALUE);
	}
	
	public boolean canCommandeer(double priority, UnitCommander checker){
		if((KaonBot.getGame().getFrameCount() - CLAIM_LOCK) < claimFrame){
			//KaonBot.print("Cannot Commandeer " + this + ": claim locked.");
			return false;
		} else if(priority < this.priority){
			//KaonBot.print("Cannot Commandeer " + this + ": priority too low.");
			return false;
		} else if(commander == checker){
			//KaonBot.print("Cannot Commandeer " + this + ": already owned by manager.");
			return false;
		}
		
		return true;
	}
	
	public boolean commandeer(UnitCommander newManager, double newClaim){
		if(!canCommandeer(newClaim, newManager)){
			return false;
		}

		for(Runnable r: onCommandeer){
			if(r instanceof CommandeerRunnable){
				if(!((CommandeerRunnable) r).disabled){
					((CommandeerRunnable) r).setNewValues(this, newManager);
					r.run();
				}
			} else {
				r.run();
			}
		}
		commander = newManager;
		priority = newClaim;
		return true;
	}
	
	public void removeCommandeerRunnable(Runnable r){
		onCommandeer.remove(r);
	}
	
	public UnitCommander getCommander(){
		return commander;
	}
	
	public double getPriority(){
		return priority;
	}
	
	@Override
	public int compareTo(Claim o) {
		return new Double(priority).compareTo(o.priority);
	}
	
	public abstract class CommandeerRunnable implements Runnable {
		Claim claim = null;
		UnitCommander newManager = null;
		Object arg;
		private boolean disabled = false;
		
		protected void setNewValues(Claim claim, UnitCommander newManager){
			this.claim = claim;
			this.newManager = newManager;
		}
		
		public CommandeerRunnable(Object arg) {
			this.arg = arg;
		}
		
		public CommandeerRunnable(){
			this(null);
		}

		public void disable(){
			disabled = true;
		}
		
		public boolean isDisabled(){
			return disabled;
		}
		
		@Override
		public abstract void run();
	}}

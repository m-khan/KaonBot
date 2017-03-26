package kaonbot;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import bwapi.Color;
import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;

public abstract class AbstractManager implements Manager{
	private double priorityScore;
	private double volitilityScore;
	protected double DO_NOT_WANT = -1;
	protected LinkedList<Claim> newUnits = new LinkedList<Claim>();
	protected Map<Integer, Claim> claimList = new HashMap<Integer, Claim>();
	protected Color debugColor;
	
	public AbstractManager(double baselinePriority, double volitilityScore) {
		priorityScore = baselinePriority;
		this.volitilityScore = volitilityScore;
		debugColor = KaonUtils.getRandomColor();
	}
	
	@Override
	public String getName(){
		return "Manager " + this.toString();
	}
	
	@Override
	public void assignNewUnit(Claim claim){
		claimList.put(claim.unit.getID(), claim);
		newUnits.add(claim);
		//KaonBot.print(getName() + " claiming " + claim.unit.getID() + " " + claim.unit.getType());
		
		// This makes sure the claim is removed if the unit is commandeered by another manager
		claim.addOnCommandeer(claim.new CommandeerRunnable(){
			@Override
			public void run() {
				//KaonBot.print(getName() + " releasing " + claim.unit.getID() + " " + claim.unit.getType());
				removeClaim(claim);
				this.disable();
			}
		});
		addCommandeerCleanup(claim);
	}
	
	protected abstract void addCommandeerCleanup(Claim claim);
	
	private void removeClaim(Claim claim){
		if(claimList.remove(claim.unit.getID()) == null){
			KaonBot.print("WARNING - Claim " + claim + " not removed properly", true);
		}
	}
	
	@Override
	public double usePriority(double multiplier) {
		if(multiplier > 1.0)
			multiplier = 1.0; // Managers cannot request more than their current priority
		
		if(priorityScore <= 0){
			return 0.000000001;
		}
		
		return priorityScore * multiplier;
	}
	
	public double getVolitility(){
		return volitilityScore;
	}
	
	public double usePriority(){
		return this.usePriority(1.0);
	}

	@Override
	public double incrementPriority(double priorityChange, boolean log) {
		if(KaonBot.getSupply() < 16) return priorityScore;
		
		KaonBot.print(getName() + " PRIORITY CHANGE:" + priorityChange);
		priorityScore += priorityChange;
		if(priorityScore < 0) priorityScore = 0;
		return priorityScore;
	}

	@Override
	public String getStatus() {
		DecimalFormat df = new DecimalFormat("##.####");
		return "PRIORITY=" + df.format(priorityScore) + "/" + df.format(volitilityScore) + "\nCLAIMS=" + claimList.size() + "\n";
	}

	public abstract class Behavior{
		private Claim unit;
		public Behavior(Claim unit){
			this.unit = unit;
		}
		public abstract boolean update();
		public Unit getUnit(){
			return unit.unit;
		}
		public UnitType getType(){
			return unit.unitType;
		}
		public void touchClaim(){
			unit.touch();
		}
	}
	
	public Claim getClaim(Integer unitID){
		return claimList.get(unitID);
	}
	
	@Override
	public List<Claim> getAllClaims(){
		ArrayList<Claim> toReturn = new ArrayList<Claim>();
		for(Integer key: claimList.keySet()){
			toReturn.add(claimList.get(key));
		}
		return toReturn;
	}
	
	@Override
	public void freeUnits() {
		for(Claim c: getAllClaims()){
			c.free();
		}
	}

	@Override
	public void displayDebugGraphics(Game game){
		for(Claim c: getAllClaims()){
			game.drawCircleMap(c.unit.getPosition(), 5, debugColor);
		}
	}

}

package kaonbot;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import bwapi.Color;
import bwapi.Game;
import bwapi.Order;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;

public class BuildingOrder extends ProductionOrder implements Comparator<ProductionOrder>{
	
	private Unit producer = null;
	private Claim tempClaim = null;
	private UnitType toProduce;
	private TilePosition position;
	private BuildManager buildManager = null;
	private boolean ignoreReservations;
	
	public BuildingOrder(int minerals, int gas, double priority, UnitType toProduce, TilePosition position) {
		this(minerals, gas, priority, toProduce, position, false);
	}

	public BuildingOrder(int minerals, int gas, double priority, UnitType toProduce, TilePosition position, boolean ignoreReservations) {
		super(ProductionOrder.BUILDING, minerals, gas, priority);
		this.ignoreReservations = ignoreReservations;
		this.toProduce = toProduce;
		this.position = position;
	}

	public TilePosition getPosition(){
		return position;
	}
	
	public UnitType getUnitType(){
		return toProduce;
	}
	
	@Override
	public boolean equals(Object o){
		BuildingOrder other = (BuildingOrder) o;
		// TODO Test this
		return position.equals(other.getPosition()) && this.getUnitType().equals(other.getUnitType());
	}
	
	@Override
	public String toString(){
		DecimalFormat df = new DecimalFormat("#.##");
		String toReturn =  toProduce + " @ " + position.toPosition() + " " + df.format(getPriority());
		if(buildManager != null){
			toReturn += "\nBuilder: " + buildManager.getAllClaims().get(0).unit.getPosition();
			toReturn += " Spent:" + this.isSpent();
		}
		
		return toReturn;
	}

	@Override
	public String getSignature(){
		return toProduce + "" + position.toPosition();
	}
	
	@Override
	public boolean execute(){
		if(!canExecute()){
			KaonBot.print("Failed to execute " + this, true);
			return false;
		}

		if(tempClaim == null){
			KaonBot.print("No claim for " + this, true);
			return false;
		}
		
		BuildManager bm = new BuildManager(tempClaim, this, this.getPriority());
//			tempClaim.addOnCommandeer(tempClaim.new CommandeerRunnable(bm) {
//				@Override
//				public void run() {
//					((BuildManager) arg).setDone();
//					setDone();
//				}
//			});
		KaonBot.addTempManager(bm);
		buildManager = bm;
		executed = true;
		
		return true;
	}
	
	@Override
	public boolean isDone(){
		if(buildManager == null){
			return true;
		}
		else{
			return super.isDone();
		}
	}
	
	private void retry(){
		producer.build(toProduce, position);
	}
	
	public boolean canExecute(){
		if(!ignoreReservations && !BuildingPlacer.getInstance().canBuildHere(position, toProduce)){
			return false;
		}
		
		if(producer == null || !producer.exists()){
			findNewProducer();
		}
		
		return producer != null && producer.exists() && KaonBot.getGame().canBuildHere(position, toProduce);
	}
	
	private void findNewProducer(){
		List<Claim> claimList = KaonBot.getAllClaims();
		tempClaim = KaonUtils.getClosestClaim(position.toPosition(), claimList, UnitType.Terran_SCV, 
				this.getPriority() * KaonBot.SCV_COMMANDEER_BUILDING_MULTIPLIER, null);
		if(tempClaim != null){
			producer = tempClaim.unit;
		}
	}
	
	private class BuildManager extends TempManager{
		private BuildingOrder order;
		private boolean started = false;
		private Color debugColor;
		private Unit building = null;
		private int retryCount = 100;
		
		private BuildManager(Claim claim, BuildingOrder order, double priority){
			super(claim);
			this.order = order;
			debugColor = KaonUtils.getRandomColor();
			
			claim.commandeer(this, priority * KaonBot.SCV_COMMANDEER_BUILDING_MULTIPLIER * 2);
			claim.addOnCommandeer(new Runnable(){
				@Override
				public void run() {
					KaonBot.print("Building order " + toString() + " commandeered.");
					onCommandeer();
				}
			});
			
			BuildingPlacer.getInstance().reserve(order.getPosition(), order.toProduce, debugColor);
			KaonBot.print(" Building " + order + " started with: " + claim.unit.getID());
		}
		
		@Override
		public String toString(){
			return order.toString() + " built by " + this.getAllClaims().get(0).unit.getID();
		}
		
		private void onCommandeer(){
			order.setDone();
			this.setDone();
			if(building != null){
				building.cancelConstruction();
			}
		}
		
		@Override
		public void runFrame() {
			if(KaonBot.getGame().getFrameCount() % 10 != 0) return;
			
			Claim claim = this.getAllClaims().get(0);
			Unit builder = claim.unit;
			if(builder.getOrder() == Order.ConstructingBuilding){
				claim.touch();
				order.setSpent();
				if(building == null && builder.getBuildUnit() != null){
					started = true;
					building = builder.getBuildUnit();
				}
			} else if(builder.isConstructing()){
				claim.touch();
				if(building == null && builder.getBuildUnit() != null){
					building = builder.getBuildUnit();
				}
			} else if(started) {
				if(!building.isBeingConstructed())
				{
					this.setDone();
				}
				if(!builder.exists()){
					BuildingPlacer.getInstance().free(order.getPosition(), order.toProduce);
					this.setDone();
				}
			} else if (builder.getPosition().equals(order.getPosition().toPosition())){
				if(retryCount >= 0){
					order.retry();
					retryCount--;
				} else {
					BuildingPlacer.getInstance().free(order.getPosition(), order.toProduce);
					this.setDone();
				}
			} else {
				if(builder.getPosition().getDistance(order.getPosition().toPosition()) > 20){
					claim.touch();
				}
				builder.attack(order.getPosition().toPosition());
				order.retry();
			}
		}
		
		@Override
		public void setDone(){
			//System.err.println("BO Done: " + building);
			if(building == null){
				BuildingPlacer.getInstance().free(order.getPosition(), order.getUnitType());
			} else if(!building.isCompleted()){
				building.cancelConstruction();
				BuildingPlacer.getInstance().free(order.getPosition(), order.getUnitType());
			}
			super.setDone();
			order.setDone();
		}
		
		@Override
		public void assignNewUnit(Claim claim) {
		}

		@Override
		public void assignNewUnitBehaviors() {
		}

		@Override
		public void displayDebugGraphics(Game game) {
			Unit builder = this.getAllClaims().get(0).unit;
			game.drawCircleMap(order.getPosition().toPosition(), 20, debugColor, false);
			game.drawCircleMap(builder.getPosition(), 10, debugColor, true);
			if(builder.isConstructing()){
				game.drawCircleMap(builder.getPosition(), 10, new Color(255, 0, 0), true);
			}
			if(building != null){
				game.drawCircleMap(building.getPosition(), 10, debugColor);
			}
		}

		@Override
		public ArrayList<Double> claimUnits(List<Unit> unitList) {
			// TODO Auto-generated method stub
			return null;
		}
	}
}


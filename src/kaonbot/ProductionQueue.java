package kaonbot;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

import bwapi.Player;


public class ProductionQueue extends PriorityQueue<ProductionOrder> {

	private static final long serialVersionUID = 2962631698946196631L;
	private Player player;
	private static ArrayList<BuildingOrder> activeOrders = new ArrayList<BuildingOrder>();
	private int OUTPUT_LIMIT = 20;
	
	public ProductionQueue(Player player){
		this.player = player;
	}
	
	public static List<BuildingOrder> getActiveOrders(){
		return activeOrders;
	}
	
	public String processQueue(){
    	StringBuilder output = new StringBuilder();
    	int outputLines = 0;
    	
		if(peek() == null){
			output.append("Empty\n");
			return output.toString();
		}
//		ArrayList<ProductionOrder> reserve = new ArrayList<ProductionOrder>();
		int min = player.minerals();
		int gas = player.gas();
		
		int freeSupply = player.supplyTotal() - player.supplyUsed();
		
		int minRes = 0;
		int gasRes = 0;
		
		// remove all finished orders from active orders
		Iterator<BuildingOrder> it = activeOrders.iterator();
		while(it.hasNext()){
			if(it.next().isDone()){
				it.remove();
			}
		}
		output.append("ACTIVE ORDERS: " + activeOrders.size() + "\n");
		outputLines++;
		
//		for(ProductionOrder o: activeOrders){
//			output.append("AO: " + o.toString() + "\n");
//		}
		
		output.append("PRODUCTION QUEUE:\n");
		outputLines++;
		
		List<ProductionOrder> processed = new LinkedList<ProductionOrder>();
		
		while(	peek() != null && 
				peek().getMinerals() <= (min - minRes) && 
				peek().getGas() <= (gas - gasRes)){
			
			ProductionOrder toExecute = poll();
			boolean isDuplicate = false;
			String currentSig = toExecute.getSignature();
			for(ProductionOrder order: activeOrders){
				if(order.getSignature().equals(currentSig)){
					isDuplicate = true;
				}
			}
			for(ProductionOrder order: processed){
				if(order.getSignature().equals(currentSig)){
					isDuplicate = true;
				}
			}
			
			if(isDuplicate){
				output.append("=" + toExecute + " - " + minRes  + "\n");
				outputLines++;
				if(!toExecute.isSpent()){
					minRes += toExecute.getMinerals();
					gasRes += toExecute.getGas();
				}
			}
			else if(toExecute.getType() == ProductionOrder.UNIT &&
					toExecute.getSupply() <= freeSupply &&
					toExecute.canExecute()){
				// Unit can be executed
				KaonBot.print("Producing " + toExecute);
				toExecute.execute();
				if(outputLines++ < OUTPUT_LIMIT) output.append("_" + toExecute + " - " + minRes + "\n");
			}
			else if(toExecute.getType() == ProductionOrder.BUILDING && toExecute.canExecute()){
				// Building can be executed
				toExecute.execute();
				activeOrders.add((BuildingOrder) toExecute);
				
				// If the building hasn't started, we still need to reserve minerals
				if(!toExecute.isSpent()){
					minRes += toExecute.getMinerals();
					gasRes += toExecute.getGas();
				}
				if(outputLines++ < OUTPUT_LIMIT) output.append("!" + toExecute + " - " + minRes  + "\n");
			} else {
				// The order is already in progress we need to wait
				if(!toExecute.isSpent()){
					minRes += toExecute.getMinerals();
					gasRes += toExecute.getGas();
				}
				if(outputLines++ < OUTPUT_LIMIT) output.append("*" + toExecute + " - " + minRes  + "\n");
			}
			processed.add(toExecute);
		}
		while(peek() != null && outputLines < OUTPUT_LIMIT){
			output.append(poll() + "\n");
			outputLines++;
		}
		
		return output.toString();
	}
}

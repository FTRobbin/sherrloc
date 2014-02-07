package diagnostic.explanation;

import java.util.Set;

import util.HTTPUtil;

/**
 * <code>Explanation</code> is the result of the error diagnosis algorithm. An
 * explanation consists of a set of entities (e.g., expressions, constraints,
 * hypotheses) and a weight w.r.t. ranking metric
 */
public class Explanation implements Comparable<Explanation> {
	private final double weight;
	private Set<Entity> entities;
	private boolean DEBUG = false;

	/**
	 * @param entities a set of entities (e.g., expressions, constraints, hypotheses)
	 * @param weight a weight w.r.t. ranking metric
	 */
	public Explanation(Set<Entity> entities, double weight) {
		this.entities = entities;
		this.weight = weight;
	}
	
	/**
	 * @return weight
	 */
	public double getWeight() {
		return weight;
	}
	
	/**
	 * @return a set of entities
	 */
	public Set<Entity> getEntities() {
		return entities;
	}
	
	@Override
	public int compareTo(Explanation o) {
		return new Double(weight).compareTo(o.weight);
	}
	
	/**
	 * For pretty print of the explanation in HTML format
	 * 
	 * @param exprMap a map from string representation of expression to corresponding graph node
	 * @return
	 */
	public String toHTML ( ) {
		StringBuffer sb = new StringBuffer();
		
		if (DEBUG) {
			sb.append("<span class=\"rank\">(score "+weight+")</span> ");
		}
		
		StringBuffer locBuffer = new StringBuffer();
    	StringBuffer exprBuffer = new StringBuffer();
		for (Entity en : entities) {
			en.toHTML(locBuffer, exprBuffer);
    	}
    	sb.append("<span class=\"path\" ");
		HTTPUtil.setShowHideActions(false, sb, locBuffer.toString(), 0);
		sb.append(">");
		sb.append(exprBuffer.toString()+"</span>");
    	sb.append("<button onclick=\"hide_all();show_elements_perm(true, [");
        sb.append(locBuffer.toString());
    	sb.append("])\" ");
		sb.append(">show it</button><br>\n");
    	
   		return sb.toString();
	}
	
	/**
	 * For pretty print of the explanation to console
	 * 
	 * @param exprMap a map from string representation of expression to corresponding graph node
	 * @return
	 */
	public String toConsole ( ) {
		StringBuffer sb = new StringBuffer();
		
		StringBuffer locBuffer = new StringBuffer();
    	StringBuffer exprBuffer = new StringBuffer();
		for (Entity en : entities) {
			en.toConsole(locBuffer, exprBuffer);
    	}
		sb.append(exprBuffer.toString()+": ");
        sb.append(locBuffer.toString());
    	
   		return sb.toString();
	}

	@Override
	public String toString() {
    	StringBuffer exprBuffer = new StringBuffer();
    	for (Entity en : entities) {
    		exprBuffer.append(en.toString()+"("+en.getSuccCount()+")    ");
    	}
    	return exprBuffer.toString();
	}
}
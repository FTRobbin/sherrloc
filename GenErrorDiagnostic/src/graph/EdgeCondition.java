package graph;

import constraint.ast.Constructor;

/**
 * Information needed for an edge representing constructors, including
 * constructor, index of the parameter, and variance.
 */
public class EdgeCondition {
	private final Constructor con;
	private final int index;
	private final boolean reverse;
	private final Polarity polarity;
	
	/**
	 * @param con
	 *            The constructor the constructed edge represents
	 * @param index
	 *            The index of the parameter, in the constructor, that the edge
	 *            is connecting to
	 * @param reverse
	 *            True if the edge is from constructor to a parameter
	 * @param pol
	 *            Variance of the parameter
	 */
	public EdgeCondition(Constructor con, int index, boolean reverse, Polarity pol) {
		this.con = con;
		this.index = index;
		this.reverse = reverse;
		this.polarity = pol;
	}
	
	/**
	 * @return True if the edge is from constructor to a parameter
	 */
	public boolean isReverse () {
		return reverse;
	}
	
	/**
	 * @return A {@link EdgeCondition} that matches this condition
	 */
	public EdgeCondition getMatch () {
		return new EdgeCondition(con, index, !reverse, polarity);
	}
	
	/**
	 * @return Variance
	 */
	public Polarity getPolarity() {
		return polarity;
	}
	
	@Override
	public String toString () {
		if (reverse)
			return con.toString()+"@"+index+"^(-1)";
		else
			return con.toString()+"@"+index;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof EdgeCondition) {
			EdgeCondition ec = (EdgeCondition) obj;
			return con.equals(ec.con) && index == ec.index 
				&& reverse == ec.reverse && polarity == ec.polarity; 
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return con.hashCode() * 1237 + index * 131 + polarity.ordinal()*5 + (reverse?1:0);
	}
	
	/**
	 * Return true if the current condition matches the parameter. That is, if
	 * <UL>
	 * <LI>They have the same constructor
	 * <LI>They have the same index
	 * <LI>Only one of them is reverse
	 * </UL>
	 * 
	 * @param c
	 *            {@link EdgeCondition}
	 * 
	 * @return True if current condition matches parameter
	 */
	public boolean matches (EdgeCondition c) {
		return (con.equals(c.con) && index==c.index && logicXOR(reverse,c.reverse) && polarity==c.polarity);
	}
	
	/**
	 * @return Logical XOR of two parameters
	 */
	private boolean logicXOR (boolean a, boolean b) {
		return (!(a&&b) && (a||b));
	}
}

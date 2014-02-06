package diagnostic;

import graph.ConstraintPath;
import graph.Edge;
import graph.ElementNode;
import graph.EquationEdge;
import graph.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.EntityExplanationFinder;
import util.HTTPUtil;
import util.HeuristicSearch;
import util.MinCutFinder;
import constraint.ast.Constraint;
import constraint.ast.Environment;
import constraint.ast.Position;

/* a set of unsatisfiable paths identified on the constraint graph */
public class UnsatPaths {
	Set<ConstraintPath> errPaths;
	// Reuse graph.env join env if the current env is already seen before
	Map<Environment, Environment> cachedEnv;	
	
	public UnsatPaths( ) {
        errPaths = new HashSet<ConstraintPath>();
        cachedEnv = new HashMap<Environment, Environment>();
    }
	
	public void addUnsatPath (ConstraintPath path) {
		errPaths.add(path);		
	}
	
	public int size () {
		return errPaths.size();
	}
	
	public Collection<ConstraintPath> getPaths () {
		return errPaths;
	}
		
	// number of missing hypotheses, used for unit test
    public int getAssumptionNumber () {
        Set<Explanation> result = MissingHypoInfer.genAssumptions(this, cachedEnv);
    	return result.size();
    }
    
    public String getAssumptionString () {
        Set<Explanation> result = MissingHypoInfer.genAssumptions(this, cachedEnv);
        StringBuffer sb = new StringBuffer();
        for (Explanation s : result) {
            List<String> list = new ArrayList<String>();
        	for (Entity en :s.getEntities())
        		list.add(en.toString());
        	Collections.sort(list);
        	for (String str : list)
        		sb.append(str+";");
        	sb.append("\n");
        }
    	return sb.toString();
    }
  	
    public Set<Explanation> genEdgeCuts (Map<String, Integer> succCount) {
    	Set<Entity> cand = new HashSet<Entity>();
		
    	for (ConstraintPath path : errPaths) {
    		for (Edge edge : path.getEdges()) {
    			if (edge instanceof EquationEdge) {
    				Constraint cons = ((EquationEdge) edge).getEquation();
    				cand.add(new ConstraintEntity(cons, cons.getSuccPaths()));
    			}
    		}
    	}
    	
    	Entity[] candarr = cand.toArray(new Entity[cand.size()]);
    	HeuristicSearch finder = new EntityExplanationFinder(this, candarr);
		
		return finder.AStarSearch();
    }
        
    public Set<Explanation> genHeuristicSnippetCut (Map<String, Integer> succCount) {
    	Set<Entity> cand = new HashSet<Entity>();
		
    	for (ConstraintPath path : errPaths) {
    		for (Node n : path.getAllNodes()) {
    			if (!((ElementNode)n).getElement().getPosition().isEmpty())
    				cand.add(new ExprEntity(n.toString(), succCount.get(n.toString())));
    		}
    	}
    	
    	Entity[] candarr = cand.toArray(new Entity[cand.size()]);
    	HeuristicSearch finder = new EntityExplanationFinder(this, candarr);
		
		return finder.AStarSearch();
    }
    
    public Set<Explanation> genSnippetCut (int max) {
    	Set<Entity> cand = new HashSet<Entity>();
		
    	for (ConstraintPath path : errPaths) {
    		for (Node n : path.getAllNodes()) {
    			if (!((ElementNode)n).getElement().getPosition().isEmpty())
    				cand.add(new ExprEntity(n.toString(), 0));
    		}
    	}
    	
    	Entity[] candarr = cand.toArray(new Entity[cand.size()]);
    	MinCutFinder cutFinder = new MinCutFinder(this, candarr);
    	
    	return cutFinder.AStarSearch();
    }
    
    
    public String toHTML ( ) {
    	StringBuffer sb = new StringBuffer();
    	sb.append("<H3>");
    	sb.append(size() +" type mismatch" + (size() == 1 ? "" : "es") + " found ");
        sb.append("<button onclick=\"show_all_errors()\">show more details</button><br>\n");
        sb.append("</H3>");
        sb.append("<div id=\"all_error\">");


		sb.append("<UL>\n");
		for (ConstraintPath path : errPaths) {
			StringBuffer path_buff = new StringBuffer();
			List<Node> nodes = path.getIdNodes();
			for (Node n : nodes) {
				path_buff.append("['pathelement', \'"+((ElementNode)n).getElement().getPosition()+"\'], ");
			}
			sb.append("<LI>\n<span class=\"path\" ");
			HTTPUtil.setShowHideActions(true, sb, path_buff.toString(), 0);
			sb.append(">");
			sb.append("A value with type "+path.getFirstElement() + 
				    " is being used at type " + path.getLastElement());
			sb.append("</span>\n");
        		sb.append("<button onclick=\"hide_all();show_elements_perm(true, [");
	        	sb.append(path_buff.toString());
        		sb.append("])\" ");
			// setShowHideActions(true, sb, path_buff.toString(), 0);
			sb.append(">show it</button><br>\n");
		}
		sb.append("</UL>\n");
        sb.append("</div>\n");
		return sb.toString();
    }
    
    public String genMissingAssumptions (Position pos, String sourceName ) {
    	StringBuffer sb = new StringBuffer();
		Set<Explanation> result = MissingHypoInfer.genAssumptions(this, cachedEnv);
		sb.append("<H3>Likely missing assumption(s): </H3>\n");
		sb.append("<UL>\n");
		
		for (Explanation g : result) {
			sb.append("<LI>");
			for (Entity en : g.getEntities()) {
				en.toHTML(null, null, sb);
			}
			sb.append("\n");
		}

		sb.append("</UL>");
		return sb.toString();
	}
    
    public String genNodeCut (Map<String, Integer> succCount, Map<String, Node> exprMap, boolean console, boolean verbose) {
    	StringBuffer sb = new StringBuffer();
		long startTime = System.currentTimeMillis();
		Set<Explanation> results = genHeuristicSnippetCut(succCount);
		long endTime =  System.currentTimeMillis();
		if (verbose)
			System.out.println("ranking_time: "+(endTime-startTime));
		
		if (!console)
			sb.append("<H4>Expressions in the source code that appear most likely to be wrong (mouse over to highlight code):</H4>\n");

		List<Explanation> cuts = new ArrayList<Explanation>();
		for (Explanation set : results) {
			cuts.add(set);
		}
		Collections.sort(cuts);

		double best=Double.MAX_VALUE;
		int i=0;
		for ( ; i<cuts.size(); i++) {
			if (cuts.get(i).getWeight()>best)
				break;
			best = cuts.get(i).getWeight();
			if (console)
				sb.append(cuts.get(i).toConsole(exprMap)+"\n");
			else
				sb.append(cuts.get(i).toHTML(exprMap));
		}
		if (verbose)
			System.out.println("top_rank_size: "+i);
		if (!console) {
			sb.append("<button onclick=\"show_more_expr()\">show/hide more</button><br>\n");
			sb.append("<div id=\"more_expr\">");
			for (; i < cuts.size(); i++) {
				sb.append(cuts.get(i).toHTML(exprMap));
			}
			sb.append("</div>\n");
		}
		return sb.toString();
    }
    
    /*
    public String genCombinedResult (Map<Environment, Environment> envs, Map<String, Node> exprMap, Map<String, Double> succCount) {
    	final Set<Hypothesis> candidates = new HashSet<Hypothesis>();
    	final Map<Environment, Environment> cachedEnv = envs;
    	StringBuffer sb = new StringBuffer();
    	for (ConstraintPath path : getPaths())
    		candidates.add(path.getMinHypo());
    	    	    	
    	CombinedExplainationFinder<Hypothesis> cutFinder = new CombinedExplainationFinder<Hypothesis>(this, exprMap, succCount) {
			@Override
			public Set<Hypothesis> mapsTo(ConstraintPath path) {
				return MissingHypoInfer.genAssumptionDep(path, candidates, cachedEnv);
			}
		};
 
		List<CombinedSuggestion<Hypothesis>> result = cutFinder.findMinCut();
 		Collections.sort(result);

		int best = Integer.MAX_VALUE;
		int i = 0;
		for (; i < result.size(); i++) {
//			if (result.get(i).rank > best)
//				break;
			best = result.get(i).rank;
			sb.append(result.get(i).toHTML(exprMap));
			System.out.println("top_rank_size: " + i);
			sb.append("<button onclick=\"show_more_expr()\">show/hide more</button><br>\n");
			sb.append("<div id=\"more_expr\">");
			for (; i < result.size(); i++) {
				sb.append(result.get(i).toHTML(exprMap));
			}
			sb.append("</div>\n");
		}
		return sb.toString();
    }
    */
    
    public String genEdgeCut (Map<String, Integer> succCount, Map<String, Node> exprMap, boolean console, boolean verbose) {
    	StringBuffer sb = new StringBuffer();
		long startTime = System.currentTimeMillis();
		Set<Explanation> results = genEdgeCuts(succCount);
		long endTime =  System.currentTimeMillis();
		if (verbose)
			System.out.println("ranking_time: "+(endTime-startTime));
		
		if (!console)
			sb.append("<H4>Constraints in the source code that appear most likely to be wrong (mouse over to highlight code):</H4>\n");

		List<Explanation> cuts = new ArrayList<Explanation>();
		for (Explanation set : results) {
			cuts.add(set);
		}
		Collections.sort(cuts);
		
		double best=Double.MAX_VALUE;
		int i=0;
		for ( ; i<cuts.size(); i++) {
			if (cuts.get(i).getWeight()>best)
				break;
			best = cuts.get(i).getWeight();
			if (console)
				sb.append(cuts.get(i).toConsole(exprMap)+"\n");
			else
				sb.append(cuts.get(i).toHTML(exprMap));
		}
		if (verbose)
			System.out.println("top_rank_size: "+i);
		if (!console) {
			sb.append("<button onclick=\"show_more_expr()\">show/hide more</button><br>\n");
			sb.append("<div id=\"more_expr\">");
			for (; i < cuts.size(); i++) {
				sb.append(cuts.get(i).toHTML(exprMap));
			}
			sb.append("</div>\n");
		}
		return sb.toString();
    }
}

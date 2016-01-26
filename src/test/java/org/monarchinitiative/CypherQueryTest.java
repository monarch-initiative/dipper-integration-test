package org.monarchinitiative;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import io.scigraph.frames.CommonProperties;
import io.scigraph.frames.Concept;
import io.scigraph.internal.CypherUtil;
import io.scigraph.neo4j.Graph;
import io.scigraph.neo4j.GraphTransactionalImpl;
import io.scigraph.neo4j.GraphUtil;
import io.scigraph.neo4j.Neo4jModule;
import io.scigraph.owlapi.OwlRelationships;
import io.scigraph.owlapi.curies.CurieUtil;
import io.scigraph.owlapi.loader.BatchOwlLoader;
import io.scigraph.owlapi.loader.OwlLoadConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class CypherQueryTest {

  @ClassRule
  public static TemporaryFolder graphDir = new TemporaryFolder();

  public static final String CONSTANT_LOCATION = "/tmp/dipper-test/";
 // public static final String CONSTANT_LOCATION = "/home/jnguyenxuan/workspace/SciGraph-playground/140Graph";

  static ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

  static CypherUtil cypherUtil;
  static GraphDatabaseService graphDb;
  static CurieUtil curieUtil;
  static Graph graph;

  Multimap<String, Object> params = HashMultimap.create();

  Transaction tx;

  @BeforeClass
  public static void loadGraph() throws Exception {
    File graphLoc = new File(CONSTANT_LOCATION);

    OwlLoadConfiguration config = MAPPER.readValue(Resources.getResource("test-load.yaml"), OwlLoadConfiguration.class);
    // config.getGraphConfiguration().setLocation(graphDir.getRoot().getAbsolutePath());
    config.getGraphConfiguration().setLocation(CONSTANT_LOCATION);
    if (!graphLoc.exists()) {
      BatchOwlLoader.load(config);
    }
    Injector i = Guice.createInjector(new Neo4jModule(config.getGraphConfiguration()), new AbstractModule() {
      @Override
      protected void configure() {
        bind(Graph.class).to(GraphTransactionalImpl.class);
      }
    });
    cypherUtil = i.getInstance(CypherUtil.class);
    curieUtil = i.getInstance(CurieUtil.class);
    graphDb = i.getInstance(GraphDatabaseService.class);
    graph = i.getInstance(Graph.class);
  }

  @Before
  public void clearParams() {
    params.clear();
  }

  @Before
  public void startTx() {
    tx = graphDb.beginTx();
  }

  @After
  public void endTx() {
    tx.failure();
    tx.close();
  }

  @Test
  @Ignore
  public void runQueries() throws URISyntaxException {
    // URL url = Resources.getResource("queries");
    // File queryDir = new File(url.toURI());
    // for (File queryFile: queryDir.listFiles()) {
    // cypherUtil.execute(query, params)
    // }
  }

  Optional<String> getCurie(Node node) {
    return curieUtil.getCurie((String) node.getProperty(Concept.IRI));
  }

  String getQuery(String methodName) throws IOException {
    methodName = methodName.substring(0, methodName.indexOf('_'));
    URL url = Resources.getResource("queries/" + methodName + ".txt");
    return Resources.toString(url, Charsets.UTF_8);
  }

  Node getParent(Node child) {
    for (Relationship r : child.getRelationships(Direction.OUTGOING, OwlRelationships.RDFS_SUBCLASS_OF)) {
      return r.getOtherNode(child);
    }
    return null;
  }

  List<String> getParents(Node child) {
    List<String> parents = new ArrayList<>();
    for (Path path : graphDb.traversalDescription().depthFirst().relationships(OwlRelationships.RDFS_SUBCLASS_OF, Direction.OUTGOING).traverse(child)) {
      if (path.length() > 1) {
        parents.add((String) path.endNode().getProperty(CommonProperties.IRI));
        /*
         * Optional<String> curie = getCurie(path.endNode()); if (curie.isPresent()) {
         * parents.add(curie.get()); }
         */
      }
    }
    return parents;
  }

  @Test
  public void geneFromPhenotype_throughFeature() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("HP:0000773").get()).get();
    params.put("phenotype_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> genes = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("gene");
      genes.add(getCurie(gene).get());
    }
    assertThat("Exact genes are returned through feature.", genes, containsInAnyOrder("NCBIGene:860"));
  }

  @Test
  @Ignore
  // This may be not working by design
  public void geneFromPhenotype_fromParent() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("HP:0000773").get()).get();
    Node parentId = getParent(graphDb.getNodeById(id));
    params.put("phenotype_id", (String) parentId.getProperty(CommonProperties.IRI));
    Result result = cypherUtil.execute(query, params);
    Set<String> genes = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("gene");
      genes.add(getCurie(gene).get());
    }
    assertThat("Parent of the phenotype includes descendant genes.", genes, containsInAnyOrder("KEGG-hsa:860", "NCBIGene:860"));
  }

  @Test
  public void geneFromPhenotype_throughGenotype() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("ZP:0005407").get()).get();
    params.put("phenotype_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> genes = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("gene");
      genes.add(getCurie(gene).get());
    }
    assertThat(genes, containsInAnyOrder("ZFIN:ZDB-GENE-980526-41", "ZFIN:ZDB-GENE-980526-166", "NCBIGene:30292"));
  }

  @Test
  public void modelFromGene_test() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("OMIM:157140").get()).get();
    params.put("gene_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> genes = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("subject");
      genes.add(getCurie(gene).get());
    }
    assertThat(genes, containsInAnyOrder("Coriell:ND02380"));
  }

  @Test
  public void modelFromGene_with_equivalent_test() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("NCBIGene:4137").get()).get();
    params.put("gene_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> genes = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("subject");
      genes.add(getCurie(gene).get());
    }
    assertThat(genes, containsInAnyOrder("Coriell:ND02380"));
  }

  @Test
  public void geneEquivalentFromGene_test() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("NCBIGene:4137").get()).get();
    params.put("gene_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> genes = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("geneEq");
      Node genee = (Node) result.next().get("gene");
      genes.add(getCurie(gene).get());
      genes.add(getCurie(genee).get());
    }
    assertThat(genes, hasItem("OMIM:157140"));
  }

  @Test
  public void variantsFromGene_zfin_test() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("ZFIN:ZDB-GENE-980526-166").get()).get();
    params.put("gene_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> variants = new HashSet<>();
    while (result.hasNext()) {
      Node variant = (Node) result.next().get("subject");
      variants.add(getCurie(variant).get());
    }
    System.out.println(variants);
    assertThat(variants, hasItem("ZFIN:ZDB-ALT-980413-636"));
  }

  @Test
  @Ignore
  public void diseaseFromPhenotype_test() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    long id = graph.getNode(curieUtil.getIri("HP:0100543").get()).get();
    params.put("phenotype_id", graph.getNodeProperty(id, CommonProperties.IRI, String.class).get());
    Result result = cypherUtil.execute(query, params);
    Set<String> diseases = new HashSet<>();
    while (result.hasNext()) {
      Node gene = (Node) result.next().get("disease");
      diseases.add(getCurie(gene).get());
    }
    assertThat(
        "Diseases are returned.",
        diseases,
        containsInAnyOrder("OMIM:100100", "OMIM:102150", "DECIPHER:1", "OMIM:607822", "OMIM:610168", "DECIPHER:14", "OMIM:158900", "OMIM:168600",
            "OMIM:194072"));
  }

  @Test
  public void labelsAreMapped() {
    long id = graph.getNode(curieUtil.getIri("ZFIN:ZDB-GENE-990415-75").get()).get();
    Node node = graphDb.getNodeById(id);
    for (String key : node.getPropertyKeys()) {
      System.out.println(key + ": " + GraphUtil.getProperties(node, key, String.class));
    }
  }

  @Test
  public void genesRelatedToVariants_byLocation() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    // long id = graph.getNode(curieUtil.getIri("HP:0100543").get()).get();
    // params.put("phenotype_id", graph.getNodeProperty(id, CommonProperties.FRAGMENT,
    // String.class).get());
    Result result = cypherUtil.execute(query, params);
    while (result.hasNext()) {
      System.out.println(result.next());
    }
  }

  @Test
  public void getChromosomes() throws Exception {
    String query =
        "START chromosomeClass = node:node_auto_index(fragment='SO_0000340') "
            + " MATCH path = (variant:`sequence feature`)-[:type]->(type)-[:subClassOf*0..]->(chromosomeClass) "
            + " RETURN variant.uri, type.uri, chromosomeClass.uri, path";

    Result result = cypherUtil.execute(query, params);
    System.out.println(result.resultAsString());
    /*
     * while (result.hasNext()) { System.out.println(result.next()); }
     */
  }

  @Test
  public void testVariants() throws Exception {
    String query =
        "  MATCH path = (subject)<-[:BFO_0000051!*]-(genotype)<-[:RO_0001000|GENO_0000222*1..2]-(organism)-[:RO_0002200|RO_0002610|RO_0002326!]->(object:Phenotype) "
            + "WHERE (((subject:`sequence feature` AND NOT subject:gene) OR subject:`variant locus`) AND NOT subject:anonymous) OR subject:`reagent targeted gene` "
            + "RETURN path ";

    Result result = cypherUtil.execute(query, params);
    System.out.println(result.resultAsString());
  }

  @Test
  public void taxonGoViolation_test() throws Exception {
    String query = getQuery(Thread.currentThread().getStackTrace()[1].getMethodName());
    String relationSet = "`http://purl.obolibrary.org/obo/BFO_0000050`|subClassOf*0..";
    List<TaxonPredicate> taxonPredicates = new ArrayList<TaxonPredicate>();
    taxonPredicates.add(new TaxonPredicate("whisker", "human", true));
    taxonPredicates.add(new TaxonPredicate("whisker", "mammal", false));
    taxonPredicates.add(new TaxonPredicate("whisker", "vertebrate", true));
    taxonPredicates.add(new TaxonPredicate("whisker", "chicken", true));
    taxonPredicates.add(new TaxonPredicate("tip_of_whisker", "human", true));
    taxonPredicates.add(new TaxonPredicate("tip_of_whisker", "mammal", false));
    taxonPredicates.add(new TaxonPredicate("tip_of_whisker", "mouse", false));
    taxonPredicates.add(new TaxonPredicate("hair", "chicken", true));
    taxonPredicates.add(new TaxonPredicate("hair_root", "chicken", true));
    taxonPredicates.add(new TaxonPredicate("hair", "bird", true));
    taxonPredicates.add(new TaxonPredicate("hair_root", "bird", true));
    taxonPredicates.add(new TaxonPredicate("whisker", "human", true));
    taxonPredicates.add(new TaxonPredicate("hair", "vertebrate", true));
    taxonPredicates.add(new TaxonPredicate("hair_root", "vertebrate", true));
    for (TaxonPredicate tp : taxonPredicates) {
      boolean result = taxonGoViolation_runner(query, tp.taxonomyClass, tp.ontologyClass, relationSet);
      assertThat("Result does not match for taxonomy " + tp.taxonomyClass + " and ontology " + tp.ontologyClass, result, is(tp.expectedResult));
    }
  }

  public boolean taxonGoViolation_runner(String query, String taxonomyClass, String ontologyClass, String relationSet) {
    Multimap<String, Object> params = HashMultimap.create();
    long taxonomyId = graph.getNode("http://scigraph.io/" + taxonomyClass).get();
    long ontologyId = graph.getNode("http://scigraph.io/" + ontologyClass).get();
    System.out.println("taxonomyId " + taxonomyId);
    System.out.println("ontologyId " + ontologyId);
    params.put("taxonomy_class", graph.getNodeProperty(taxonomyId, CommonProperties.IRI, String.class).get());
    params.put("ontology_class", graph.getNodeProperty(ontologyId, CommonProperties.IRI, String.class).get());
    params.put("relation_set", relationSet);
    Result result = cypherUtil.execute(query, params);
    boolean returnVal = false;
    System.out.println(taxonomyClass + " - " + ontologyClass);
    while (result.hasNext()) {
      Map<String, Object> map = result.next();
      returnVal = (boolean) map.get("result");
      System.out.println(map.get("result"));
      System.out.println("path " + map.get("path"));
      System.out.println("path2 " + map.get("path2"));
    }
    return returnVal;
  }


}

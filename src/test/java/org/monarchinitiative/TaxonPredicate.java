package org.monarchinitiative;

public class TaxonPredicate {

  public String taxonomyClass;
  public String ontologyClass;
  public boolean expectedResult;

  public TaxonPredicate(String ontologyClass, String taxonomyClass, boolean expectedResult) {
    this.ontologyClass = ontologyClass;
    this.taxonomyClass = taxonomyClass;
    this.expectedResult = expectedResult;
  }


}

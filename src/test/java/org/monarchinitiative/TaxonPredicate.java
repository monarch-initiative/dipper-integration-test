package org.monarchinitiative;

public class TaxonPredicate {

  public String taxonomyClass;
  public String ontologyClass;
  public boolean expectedResult;

  public TaxonPredicate(String taxonomyClass, String ontologyClass, boolean expectedResult) {
    this.taxonomyClass = taxonomyClass;
    this.ontologyClass = ontologyClass;
    this.expectedResult = expectedResult;
  }


}

package moa.classifiers;

public interface FeatureScorer {

    /**
     * Returns an array with scores given the current model learned.
     * This array will have the same length as the input feature array, while ignoring the class.
     *
     * @return the score array
     */
    double[] getFeatureScores();

}

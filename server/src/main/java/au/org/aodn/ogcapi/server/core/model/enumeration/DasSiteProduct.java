package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * The site products DAS serves under /data/feature-collection/{product}.
 */
public enum DasSiteProduct {
    WAVE_BUOY("wave-buoy", "wave buoy", "waveBuoy"),
    MOORING("mooring", "mooring", "mooring"),
    MOORING_ESTUARINE_WATER_QUALITY("mooring-estuarine-water-quality", "estuarine water quality mooring", "mooring");

    public final String path; // The {product} segment of the DAS url
    public final String label; // Singular name of a site, used in log messages
    public final String siteParameter; // The FeatureRequest parameter that names a site

    DasSiteProduct(String path, String label, String siteParameter) {
        this.path = path;
        this.label = label;
        this.siteParameter = siteParameter;
    }
}

package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import lombok.Setter;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LikeToRegexConverter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.PropertyIsLike;
import org.opengis.filter.expression.Expression;

public class LikeImpl<T extends Enum<T> & CQLFieldsInterface> extends QueryHandler implements PropertyIsLike {

    protected Expression expression;
    protected String literal;
    protected String pattern;

    @Setter
    protected String wildcard = "%";
    @Setter
    protected String singleChar = "_";
    @Setter
    protected String escapeChar = "\\";

    public LikeImpl(Expression expression, String literal, Class<T> enumType) {
        if(expression instanceof AttributeExpressionImpl attribute) {
            this.expression = expression;
            this.literal = literal;

            // Use function comes from geotools, but this required class to implement method below
            this.pattern = (new LikeToRegexConverter(this)).getPattern();

            /*
             * Given the field do set with default in most case hence the indexer lower case for all value, therefore
             * if we want to match CAP letter search, we need to set caseInsensitive true
             *
             * It depends on the mapping you have defined for you field name. If you haven't defined any mapping then
             * elasticsearch will treat it as string and use the standard analyzer (which lower-cases the tokens) to
             * generate tokens. Your query will also use the same analyzer for search hence matching is done by
             * lower-casing the input. That's why "Binoy" matches "binoy"
             *
             * To solve it you can define a custom analyzer without lowercase filter and use it for your field name.
             * You can define the analyzer as below
             */
            T v = Enum.valueOf(enumType, expression.toString().toLowerCase());
            this.query = v.getLikeQuery(this.pattern);
        }
    }

    @Override
    public Expression getExpression() {
        return this.expression;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getWildCard() {
        return wildcard;
    }

    @Override
    public String getSingleChar() {
        return singleChar;
    }

    @Override
    public String getEscape() {
        return escapeChar;
    }

    @Override
    public boolean isMatchingCase() {
        return true;
    }

    @Override
    public MatchAction getMatchAction() {
        return null;
    }

    @Override
    public boolean evaluate(Object o) {
        return false;
    }

    @Override
    public Object accept(FilterVisitor filterVisitor, Object o) {
        return null;
    }
}

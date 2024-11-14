package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLElasticSetting;
import lombok.Getter;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.PropertyIsGreaterThanOrEqualTo;
import org.opengis.filter.expression.Expression;

/**
 * Some property is elastic setting not search field of STAC
 */
public class PropertyIsGreaterThanOrEqualToElasticSettingImpl implements PropertyIsGreaterThanOrEqualTo, ElasticSetting {
    protected Expression expression1;
    protected Expression expression2;

    @Getter
    protected boolean valid;

    @Getter
    protected CQLElasticSetting elasticSettingName;

    @Getter
    protected String elasticSettingValue;

    public PropertyIsGreaterThanOrEqualToElasticSettingImpl(Expression expression1, Expression expression2) {
        this.expression1 = expression1;
        this.expression2 = expression2;

        if (expression1 instanceof AttributeExpressionImpl attribute && expression2 instanceof LiteralExpressionImpl literal) {
            try {
                elasticSettingName = Enum.valueOf(CQLElasticSetting.class, attribute.toString().toLowerCase());
                elasticSettingValue = literal.toString();
                valid = true;
            }
            catch (IllegalArgumentException e) {
                this.elasticSettingName = null;
                this.elasticSettingValue = null;
            }
        }
    }

    @Override
    public Expression getExpression1() {
        return null;
    }

    @Override
    public Expression getExpression2() {
        return null;
    }

    @Override
    public boolean isMatchingCase() {
        return false;
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

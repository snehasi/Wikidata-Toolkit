package org.wikidata.wdtk.datamodel.json.jackson;

/*
 * #%L
 * Wikidata Toolkit Data Model
 * %%
 * Copyright (C) 2014 Wikidata Toolkit Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.lang3.StringUtils;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.helpers.DatamodelConverter;
import org.wikidata.wdtk.datamodel.helpers.DatamodelMapper;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.datamodel.json.jackson.datavalues.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory implementation to create Jackson versions of the datamodel objects,
 * where available.
 *
 * @author Markus Kroetzsch
 *
 */
public class JacksonObjectFactory implements DataObjectFactory {

	private final DatamodelConverter dataModelConverter = new DatamodelConverter(
			this);

	private static final Pattern DATATYPE_ID_PATTERN = Pattern.compile("^http://wikiba\\.se/ontology#([a-zA-Z]+)$");

	@Override
	public ItemIdValue getItemIdValue(String id, String siteIri) {
		JacksonValueItemId result = new JacksonValueItemId(new JacksonInnerEntityId(id), siteIri);
		return result;
	}

	@Override
	public PropertyIdValue getPropertyIdValue(String id, String siteIri) {
		JacksonValuePropertyId result = new JacksonValuePropertyId(new JacksonInnerEntityId(id), siteIri);
		return result;
	}

	@Override
	public DatatypeIdValue getDatatypeIdValue(String id) {
		return Datamodel.makeDatatypeIdValue(id);
	}

	@Override
	public TimeValue getTimeValue(long year, byte month, byte day, byte hour,
			byte minute, byte second, byte precision, int beforeTolerance,
			int afterTolerance, int timezoneOffset, String calendarModel) {
		JacksonInnerTime innerTime = new JacksonInnerTime(year, month, day,
				hour, minute, second, timezoneOffset, beforeTolerance,
				afterTolerance, precision, calendarModel);
		JacksonValueTime result = new JacksonValueTime(innerTime);
		return result;
	}

	@Override
	public GlobeCoordinatesValue getGlobeCoordinatesValue(double latitude,
			double longitude, double precision, String globeIri) {
		if (precision <= 0) {
			throw new IllegalArgumentException(
					"Coordinates precision must be non-zero positive. Given value: "
							+ precision);
		}
		JacksonInnerGlobeCoordinates innerCoordinates = new JacksonInnerGlobeCoordinates(
				latitude,
				longitude,
				precision,
				globeIri);
		JacksonValueGlobeCoordinates result = new JacksonValueGlobeCoordinates(innerCoordinates);
		return result;
	}

	@Override
	public StringValue getStringValue(String string) {
		JacksonValueString result = new JacksonValueString(string);
		return result;
	}

	@Override
	public MonolingualTextValue getMonolingualTextValue(String text,
			String languageCode) {
		JacksonInnerMonolingualText innerMtlv = new JacksonInnerMonolingualText(
				languageCode, text);
		JacksonValueMonolingualText result = new JacksonValueMonolingualText(innerMtlv);
		return result;
	}


	@Override
	public QuantityValue getQuantityValue(BigDecimal numericValue) {
		return getQuantityValue(numericValue, null, null, "");
	}

	@Override
	public QuantityValue getQuantityValue(BigDecimal numericValue,
			BigDecimal lowerBound, BigDecimal upperBound) {
		return getQuantityValue(numericValue, lowerBound, upperBound, "");
	}

	@Override
	public QuantityValue getQuantityValue(BigDecimal numericValue, String unit) {
		return getQuantityValue(numericValue, null, null, unit);
	}

	@Override
	public QuantityValue getQuantityValue(BigDecimal numericValue,
			BigDecimal lowerBound, BigDecimal upperBound, String unit) {
		JacksonInnerQuantity innerQuantity = new JacksonInnerQuantity(
				numericValue, upperBound, lowerBound, unit);
		JacksonValueQuantity result = new JacksonValueQuantity(innerQuantity);
		return result;
	}

	/**
	 * Creates a {@link JacksonValueSnak}. Value snaks in JSON need to know the
	 * datatype of their property, which is not given in the parameters of this
	 * method. The snak that will be returned will use a default type based on
	 * the kind of value that is used (usually the "simplest" type for that
	 * value). This may not be desired.
	 *
	 * @see DataObjectFactory#getValueSnak(PropertyIdValue, Value)
	 */
	@Override
	public ValueSnak getValueSnak(PropertyIdValue propertyId, Value value) {
		if (value instanceof JacksonValue) {
			return getJacksonValueSnak(propertyId, (JacksonValue) value,
					getJsonPropertyTypeForValueType(value));
		} else {
			return getJacksonValueSnak(propertyId,
					(JacksonValue) this.dataModelConverter.copyValue(value),
					getJsonPropertyTypeForValueType(value));
		}
	}

	@Override
	public SomeValueSnak getSomeValueSnak(PropertyIdValue propertyId) {
		JacksonSomeValueSnak result = new JacksonSomeValueSnak(
				propertyId.getId(),
				propertyId.getSiteIri());
		return result;
	}

	@Override
	public NoValueSnak getNoValueSnak(PropertyIdValue propertyId) {
		JacksonNoValueSnak result = new JacksonNoValueSnak(
				propertyId.getId(),
				propertyId.getSiteIri());
		return result;
	}

	@Override
	public SnakGroup getSnakGroup(List<? extends Snak> snaks) {
		return Datamodel.makeSnakGroup(convertSnakList(snaks));
	}

	@Override
	public Claim getClaim(EntityIdValue subject, Snak mainSnak,
			List<SnakGroup> qualifiers) {
		// Jackson claims cannot exist without a statement.
		Statement statement = getStatement(
				Datamodel.makeClaim(subject, mainSnak, qualifiers),
				Collections.<Reference> emptyList(), StatementRank.NORMAL,
				"empty id 12345");
		return statement.getClaim();
	}

	@Override
	public Reference getReference(List<SnakGroup> snakGroups) {
		Map<String, List<JacksonSnak>> snakMap = new HashMap<>();
		List<String> propertyOrder = new ArrayList<>(snakGroups.size());
		for (SnakGroup snakGroup : snakGroups) {
			snakMap.put(snakGroup.getProperty().getId(),
					convertSnakList(snakGroup.getSnaks()));
			propertyOrder.add(snakGroup.getProperty().getId());
		}
		return new JacksonReference(snakMap, propertyOrder);
	}

	@Override
	public Statement getStatement(Claim claim,
			List<? extends Reference> references, StatementRank rank,
			String statementId) {

	    JacksonSnak mainsnak = null;
		if (claim.getMainSnak() instanceof JacksonSnak) {
			mainsnak = (JacksonSnak) claim.getMainSnak();
		} else {
			mainsnak = (JacksonSnak) dataModelConverter.copySnak(claim
					.getMainSnak());
		}

		Map<String, List<JacksonSnak>> qualifiers = new HashMap<>();
		List<String> propertyOrder = new ArrayList<>(claim.getQualifiers()
				.size());
		for (SnakGroup sg : claim.getQualifiers()) {
			qualifiers.put(sg.getProperty().getId(),
					convertSnakList(sg.getSnaks()));
			propertyOrder.add(sg.getProperty().getId());
		};

		List<JacksonReference> jacksonReferences = new ArrayList<>(
				references.size());
		for (Reference reference : references) {
			if (reference instanceof JacksonReference) {
				jacksonReferences.add((JacksonReference) reference);
			} else {
				jacksonReferences.add((JacksonReference) dataModelConverter
						.copy(reference));
			}
		}
		return new JacksonStatement(statementId,
				rank, mainsnak, qualifiers, propertyOrder,
				jacksonReferences, claim.getSubject());
	}

	@Override
	public StatementGroup getStatementGroup(List<Statement> statements) {
		List<Statement> newStatements = new ArrayList<>(statements.size());
		for (Statement statement : statements) {
			if (statement instanceof JacksonPreStatement) {
				newStatements.add(statement);
			} else {
				newStatements.add(this.dataModelConverter.copy(statement));
			}
		}
		return Datamodel.makeStatementGroup(newStatements);
	}

	@Override
	public SiteLink getSiteLink(String title, String siteKey,
			List<String> badges) {
		JacksonSiteLink result = new JacksonSiteLink(
				title, siteKey, badges);
		return result;
	}

	@Override
	public PropertyDocument getPropertyDocument(PropertyIdValue propertyId,
			List<MonolingualTextValue> labels,
			List<MonolingualTextValue> descriptions,
			List<MonolingualTextValue> aliases, DatatypeIdValue datatypeId) {
		return getPropertyDocument(propertyId, labels, descriptions, aliases,
				Collections.<StatementGroup> emptyList(), datatypeId, 0);
	}

	@Override
	public PropertyDocument getPropertyDocument(PropertyIdValue propertyId,
			List<MonolingualTextValue> labels,
			List<MonolingualTextValue> descriptions,
			List<MonolingualTextValue> aliases,
			List<StatementGroup> statementGroups, DatatypeIdValue datatypeId) {
		return getPropertyDocument(propertyId, labels, descriptions, aliases,
				statementGroups, datatypeId, 0);
	}

	@Override
	public PropertyDocument getPropertyDocument(PropertyIdValue propertyId,
			List<MonolingualTextValue> labels,
			List<MonolingualTextValue> descriptions,
			List<MonolingualTextValue> aliases,
			List<StatementGroup> statementGroups, DatatypeIdValue datatypeId,
			long revisionId) {
		JacksonPropertyDocument result = new JacksonPropertyDocument(
				propertyId.getId(),
				buildTermMapFromTermList(labels),
				buildTermMapFromTermList(descriptions),
				buildAliasMapFromAliasList(aliases),
				buildStatementMapFromStatementGroups(statementGroups),
				convertDatatype(datatypeId),
				revisionId,
				propertyId.getSiteIri());
		return result;
	}

	@Override
	public ItemDocument getItemDocument(ItemIdValue itemIdValue,
			List<MonolingualTextValue> labels,
			List<MonolingualTextValue> descriptions,
			List<MonolingualTextValue> aliases,
			List<StatementGroup> statementGroups,
			Map<String, SiteLink> siteLinks) {
		return getItemDocument(itemIdValue, labels, descriptions, aliases,
				statementGroups, siteLinks, 0);
	}

	@Override
	public ItemDocument getItemDocument(ItemIdValue itemIdValue,
			List<MonolingualTextValue> labels,
			List<MonolingualTextValue> descriptions,
			List<MonolingualTextValue> aliases,
			List<StatementGroup> statementGroups,
			Map<String, SiteLink> siteLinks, long revisionId) {

		Map<String, JacksonSiteLink> jacksonSiteLinks = new HashMap<>(
				siteLinks.size());
		for (SiteLink siteLink : siteLinks.values()) {
			if (siteLink instanceof JacksonSiteLink) {
				jacksonSiteLinks.put(siteLink.getSiteKey(),
						(JacksonSiteLink) siteLink);
			} else {
				jacksonSiteLinks.put(siteLink.getSiteKey(),
						(JacksonSiteLink) this.dataModelConverter
								.copy(siteLink));
			}
		}
		JacksonItemDocument result = new JacksonItemDocument(
				itemIdValue.getId(),
				buildTermMapFromTermList(labels),
				buildTermMapFromTermList(descriptions),
				buildAliasMapFromAliasList(aliases),
				buildStatementMapFromStatementGroups(statementGroups),
				jacksonSiteLinks,
				revisionId,
				itemIdValue.getSiteIri());

		return result;
	}
	
	private Map<String, List<JacksonMonolingualTextValue>> buildAliasMapFromAliasList(List<MonolingualTextValue> aliases) {
		Map<String, List<JacksonMonolingualTextValue>> aliasMap = new HashMap<>();
		for (MonolingualTextValue mltv : aliases) {
			List<JacksonMonolingualTextValue> langAliases = aliasMap.get(mltv
					.getLanguageCode());
			if (langAliases == null) {
				langAliases = new ArrayList<>();
				aliasMap.put(mltv.getLanguageCode(), langAliases);
			}
			langAliases.add(convertMltv(mltv));
		}
		return aliasMap;
	}
	
	private Map<String, List<JacksonPreStatement>> buildStatementMapFromStatementGroups(List<StatementGroup> statementGroups) {
		Map<String, List<JacksonPreStatement>> jacksonStatements = new HashMap<>();
		for (StatementGroup sg : statementGroups) {
			String propertyId = sg.getProperty().getId();
			List<JacksonPreStatement> propertyStatements = new ArrayList<>(sg
					.getStatements().size());
			jacksonStatements.put(propertyId, propertyStatements);

			for (Statement s : sg) {
				if (s instanceof JacksonPreStatement) {
					propertyStatements.add((JacksonPreStatement) s);
				} else {
					propertyStatements
							.add((JacksonPreStatement) this.dataModelConverter
									.copy(s));
				}
			}
		}
		return jacksonStatements;
	}

	private Map<String, JacksonMonolingualTextValue> buildTermMapFromTermList(
			List<MonolingualTextValue> terms) {
		Map<String, JacksonMonolingualTextValue> result = new HashMap<>(
				terms.size());
		for (MonolingualTextValue mltv : terms) {
			result.put(mltv.getLanguageCode(), convertMltv(mltv));
		}
		return result;
	}

	private JacksonMonolingualTextValue convertMltv(MonolingualTextValue mltv) {
		if (mltv instanceof JacksonMonolingualTextValue) {
			return (JacksonMonolingualTextValue) mltv;
		} else {
			return new JacksonMonolingualTextValue(mltv);
		}
	}

	private ValueSnak getJacksonValueSnak(PropertyIdValue propertyId,
			JacksonValue value, String propertyDatatype) {
		JacksonValueSnak result = new JacksonValueSnak(
				propertyId.getId(),
				propertyDatatype,
				value,
				propertyId.getSiteIri());
		return result;
	}

	private String getJsonPropertyTypeForValueType(Value value) {
		if (value instanceof TimeValue) {
			return JacksonDatatypeId.JSON_DT_TIME;
		} else if (value instanceof ItemIdValue) {
			return JacksonDatatypeId.JSON_DT_ITEM;
		} else if (value instanceof PropertyIdValue) {
			return JacksonDatatypeId.JSON_DT_PROPERTY;
		} else if (value instanceof StringValue) {
			return null;
		} else if (value instanceof GlobeCoordinatesValue) {
			return JacksonDatatypeId.JSON_DT_GLOBE_COORDINATES;
		} else if (value instanceof QuantityValue) {
			return JacksonDatatypeId.JSON_DT_QUANTITY;
		} else if (value instanceof MonolingualTextValue) {
			return JacksonDatatypeId.JSON_DT_MONOLINGUAL_TEXT;
		} else {
			throw new UnsupportedOperationException("Unsupported value type "
					+ value.getClass());
		}
	}

	private List<JacksonSnak> convertSnakList(List<? extends Snak> snaks) {
		List<JacksonSnak> result = new ArrayList<>(snaks.size());
		for (Snak snak : snaks) {
			if (snak instanceof JacksonSnak) {
				result.add((JacksonSnak) snak);
			} else {
				result.add((JacksonSnak) dataModelConverter.copySnak(snak));
			}
		}
		return result;
	}

	private String convertDatatype(DatatypeIdValue datatypeId) {
		switch (datatypeId.getIri()) {
			case DatatypeIdValue.DT_ITEM:
				return JacksonDatatypeId.JSON_DT_ITEM;
			case DatatypeIdValue.DT_GLOBE_COORDINATES:
				return JacksonDatatypeId.JSON_DT_GLOBE_COORDINATES;
			case DatatypeIdValue.DT_URL:
				return JacksonDatatypeId.JSON_DT_URL;
			case DatatypeIdValue.DT_COMMONS_MEDIA:
				return JacksonDatatypeId.JSON_DT_COMMONS_MEDIA;
			case DatatypeIdValue.DT_TIME:
				return JacksonDatatypeId.JSON_DT_TIME;
			case DatatypeIdValue.DT_QUANTITY:
				return JacksonDatatypeId.JSON_DT_QUANTITY;
			case DatatypeIdValue.DT_STRING:
				return JacksonDatatypeId.JSON_DT_STRING;
			case DatatypeIdValue.DT_MONOLINGUAL_TEXT:
				return JacksonDatatypeId.JSON_DT_MONOLINGUAL_TEXT;
			case DatatypeIdValue.DT_PROPERTY:
				return JacksonDatatypeId.JSON_DT_PROPERTY;
			default:
				//We apply the reverse algorithm of JacksonDatatypeId::getDatatypeIriFromJsonDatatype
				Matcher matcher = DATATYPE_ID_PATTERN.matcher(datatypeId.getIri());
				if(!matcher.matches()) {
					throw new IllegalArgumentException("Unknown datatype: " + datatypeId.getIri());
				}

				StringBuilder jsonDatatypeBuilder = new StringBuilder();
				for(char ch : StringUtils.uncapitalize(matcher.group(1)).toCharArray()) {
					if(Character.isUpperCase(ch)) {
						jsonDatatypeBuilder
								.append('-')
								.append(Character.toLowerCase(ch));
					} else {
						jsonDatatypeBuilder.append(ch);
					}
				}
				return jsonDatatypeBuilder.toString();
		}
	}
}

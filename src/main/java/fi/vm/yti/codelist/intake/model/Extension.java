package fi.vm.yti.codelist.intake.model;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonView;

import fi.vm.yti.codelist.common.dto.Views;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import static fi.vm.yti.codelist.common.constants.ApiConstants.LANGUAGE_CODE_EN;

@Entity
@JsonFilter("extension")
@Table(name = "extension")
@XmlRootElement
@XmlType(propOrder = { "id", "code", "prefLabel", "startDate", "endDate", "extensionValue", "order", "extensionScheme", "extension" })
@ApiModel(value = "Extension", description = "Extension model that represents data for one extension element.")
public class Extension extends AbstractIdentifyableTimestampedCode implements Serializable {

    private static final long serialVersionUID = 1L;

    private String extensionValue;
    private Integer order;
    private Code code;
    private ExtensionScheme extensionScheme;
    private Extension extension;
    private Map<String, String> prefLabel;
    private Date startDate;
    private Date endDate;

    @Column(name = "extensionvalue")
    @JsonView(Views.Normal.class)
    public String getExtensionValue() {
        return extensionValue;
    }

    public void setExtensionValue(final String extensionValue) {
        this.extensionValue = extensionValue;
    }

    @Column(name = "extensionorder")
    @JsonView(Views.Normal.class)
    public Integer getOrder() {
        return order;
    }

    public void setOrder(final Integer order) {
        this.order = order;
    }

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.DETACH)
    @JsonView(Views.ExtendedExtension.class)
    @JoinColumn(name = "code_id", nullable = false)
    public Code getCode() {
        return code;
    }

    public void setCode(final Code code) {
        this.code = code;
    }

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.DETACH)
    @JoinColumn(name = "extensionscheme_id", updatable = false)
    @JsonView(Views.ExtendedExtension.class)
    public ExtensionScheme getExtensionScheme() {
        return extensionScheme;
    }

    public void setExtensionScheme(final ExtensionScheme extensionScheme) {
        this.extensionScheme = extensionScheme;
    }

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.DETACH)
    @JoinColumn(name = "extension_id")
    @JsonView(Views.ExtendedExtension.class)
    public Extension getExtension() {
        return extension;
    }

    public void setExtension(final Extension extension) {
        this.extension = extension;
    }

    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "extension_preflabel", joinColumns = @JoinColumn(name = "extension_id", referencedColumnName = "id"))
    @MapKeyColumn(name = "language")
    @Column(name = "preflabel")
    @OrderColumn
    @JsonView(Views.Normal.class)
    public Map<String, String> getPrefLabel() {
        if (prefLabel == null) {
            prefLabel = new HashMap<>();
        }
        return prefLabel;
    }

    public void setPrefLabel(final Map<String, String> prefLabel) {
        this.prefLabel = prefLabel;
    }

    public String getPrefLabel(final String language) {
        String prefLabelValue = this.prefLabel.get(language);
        if (prefLabelValue == null) {
            prefLabelValue = this.prefLabel.get(LANGUAGE_CODE_EN);
        }
        return prefLabelValue;
    }

    public void setPrefLabel(final String language, final String value) {
        if (prefLabel == null) {
            prefLabel = new HashMap<>();
        }
        if (language != null && value != null && !value.isEmpty()) {
            prefLabel.put(language, value);
        } else if (language != null) {
            prefLabel.remove(language);
        }
        setPrefLabel(prefLabel);
    }

    @ApiModelProperty(dataType = "dateTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Temporal(TemporalType.DATE)
    @Column(name = "startdate")
    @JsonView(Views.Normal.class)
    public Date getStartDate() {
        if (startDate != null) {
            return new Date(startDate.getTime());
        }
        return null;
    }

    public void setStartDate(final Date startDate) {
        if (startDate != null) {
            this.startDate = new Date(startDate.getTime());
        } else {
            this.startDate = null;
        }
    }

    @ApiModelProperty(dataType = "dateTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Temporal(TemporalType.DATE)
    @Column(name = "enddate")
    @JsonView(Views.Normal.class)
    public Date getEndDate() {
        if (endDate != null) {
            return new Date(endDate.getTime());
        }
        return null;
    }

    public void setEndDate(final Date endDate) {
        if (endDate != null) {
            this.endDate = new Date(endDate.getTime());
        } else {
            this.endDate = null;
        }
    }
}
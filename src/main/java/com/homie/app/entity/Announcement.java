package com.homie.app.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A single house notice on the Announcements board — e.g. "Quiet hours
 * start at 11pm", "Kitchen deep clean this Saturday". Any housemate can
 * post one; only the person who posted it can delete it.
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    // e.g. "House rules", "Cleaning", "Bins", "Maintenance", "Social", "Other".
    // Kept as a free String rather than an enum, matching how Bill.category
    // is stored, so the category list is easy to change later.
    @Column(nullable = false)
    private String category;

    // The full notice text. Can contain multiple paragraphs, separated by a
    // blank line — see getParagraphs().
    @Lob
    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private LocalDate createdDate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    public Announcement() {
    }

    public Announcement(String title, String category, String body, LocalDate createdDate, User createdBy) {
        this.title = title;
        this.category = category;
        this.body = body;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    // A short one-line preview for the list view, cut to ~140 characters at
    // a word boundary rather than mid-word.
    @Transient
    public String getExcerpt() {
        if (body == null) {
            return "";
        }
        String firstParagraph = body.split("\\R\\s*\\R", 2)[0].trim();
        if (firstParagraph.length() <= 140) {
            return firstParagraph;
        }
        int cut = firstParagraph.lastIndexOf(' ', 140);
        if (cut < 0) {
            cut = 140;
        }
        return firstParagraph.substring(0, cut).trim() + "…";
    }

    // Splits the body into paragraphs on blank lines, for the detail page.
    @Transient
    public List<String> getParagraphs() {
        List<String> paragraphs = new ArrayList<>();
        if (body == null) {
            return paragraphs;
        }
        for (String part : body.split("\\R\\s*\\R")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    // "Jun 22" style label used in the meta line ("Julia · Jun 22").
    @Transient
    public String getDateLabel() {
        return createdDate == null ? "" : createdDate.format(DATE_LABEL);
    }
}

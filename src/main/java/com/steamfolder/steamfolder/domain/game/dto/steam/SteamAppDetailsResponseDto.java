package com.steamfolder.steamfolder.domain.game.dto.steam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamAppDetailsResponseDto {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("data")
    private AppData data;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppData {
        @JsonProperty("steam_appid")
        private Long steamAppId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("header_image")
        private String headerImage;

        @JsonProperty("background")
        private String background;

        @JsonProperty("developers")
        private List<String> developers = new ArrayList<>();

        @JsonProperty("genres")
        private List<GenreDto> genres = new ArrayList<>();

        @JsonProperty("release_date")
        private ReleaseDateDto releaseDate;

        public List<String> getGenreNames() {
            if (genres == null) {
                return new ArrayList<>();
            }
            return genres.stream()
                    .map(GenreDto::getDescription)
                    .toList();
        }

        public String getDeveloperName() {
            if (developers == null || developers.isEmpty()) {
                return null;
            }
            return String.join(", ", developers);
        }

        public String getReleaseDateString() {
            return releaseDate != null ? releaseDate.getDate() : null;
        }
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenreDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("description")
        private String description;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReleaseDateDto {
        @JsonProperty("date")
        private String date;
    }
}

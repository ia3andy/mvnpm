package org.mvnpm.npm.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represent a Name from both NPM and Maven
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@JsonDeserialize(using = NameDeserializer.class)
public record Name (
    String npmFullName,
    String npmNamespace, 
    String npmName, 
    String mvnGroupId, 
    String mvnArtifactId,
    String mvnPath,
    String displayName){
    
    public Name(String npmFullName, String npmNamespace, String npmName, String mvnGroupId, String mvnArtifactId, String mvnPath, String displayName) {
        if(npmNamespace == null && npmName == null && mvnGroupId == null && mvnArtifactId == null && mvnPath == null && displayName == null){
            Name parsed = NameParser.fromNpmProject(npmFullName);
            this.npmFullName = parsed.npmFullName();
            this.npmNamespace = parsed.npmNamespace();
            this.npmName = parsed.npmName();
            this.mvnGroupId = parsed.mvnGroupId();
            this.mvnArtifactId = parsed.mvnArtifactId();
            this.mvnPath = parsed.mvnPath();
            this.displayName = parsed.displayName();
        }else{
            this.npmFullName = npmFullName;
            this.npmNamespace = npmNamespace;
            this.npmName = npmName;
            this.mvnGroupId = mvnGroupId;
            this.mvnArtifactId = mvnArtifactId;
            this.mvnPath = mvnPath;
            this.displayName = displayName;
        }   
    }
    
    public Name(String npmFullName) {
        this(npmFullName, null, null, null, null, null, null);
    }
    
    @Override
    public String toString() {
        return this.npmFullName;
    }
}
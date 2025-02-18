package org.mvnpm.file.type;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.file.AsyncFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Properties;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.mvnpm.Constants;
import static org.mvnpm.Constants.CLOSE_ROUND;
import static org.mvnpm.Constants.COMMA;
import static org.mvnpm.Constants.OPEN_BLOCK;
import org.mvnpm.file.FileStore;
import org.mvnpm.npm.NpmRegistryFacade;
import org.mvnpm.npm.model.Bugs;
import org.mvnpm.npm.model.Name;
import org.mvnpm.npm.model.Maintainer;
import org.mvnpm.npm.model.Project;
import org.mvnpm.npm.model.Repository;
import org.mvnpm.version.VersionConverter;

/**
 * Creates a pom.xml from the NPM Package
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@ApplicationScoped
public class PomClient {
    
    @Inject 
    FileStore fileCreator;
    
    @Inject
    NpmRegistryFacade npmRegistryFacade;
    
    private final MavenXpp3Writer mavenXpp3Writer = new MavenXpp3Writer();
    
    public Uni<AsyncFile> createPom(org.mvnpm.npm.model.Package p, String localFileName) {     
        Uni<byte[]> contents = writePomToBytes(p);
        return contents.onItem().transformToUni((c) -> {
            return fileCreator.createFile(p, localFileName, c);
        });
    }
    
    private Uni<byte[]> writePomToBytes(org.mvnpm.npm.model.Package p) {
        
        Uni<List<Dependency>> toDependencies = toDependencies(p.dependencies());
        return toDependencies.onItem().transform((var deps) -> {
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()){
                Model model = new Model();
                
                model.setModelVersion(MODEL_VERSION);
                model.setGroupId(p.name().mvnGroupId());
                model.setArtifactId(p.name().mvnArtifactId());
                model.setVersion(p.version());
                model.setPackaging(JAR);
                model.setName(p.name().displayName());
                model.setDescription(p.description());
                model.setLicenses(toLicenses(p.license()));
                if(p.homepage()!=null)model.setUrl(p.homepage().toString());
                model.setOrganization(toOrganization(p));
                model.setScm(toScm(p.repository()));
                model.setIssueManagement(toIssueManagement(p.bugs()));
                model.setDevelopers(toDevelopers(p.maintainers()));
                if(!deps.isEmpty()){
                    Properties properties = new Properties();
                    
                    for(Dependency dep:deps){
                        String version = dep.getVersion();
                        String propertyKey = dep.getGroupId() + Constants.HYPHEN + dep.getArtifactId() + Constants.DOT + Constants.VERSION;
                        properties.put(propertyKey, version);
                        dep.setVersion(Constants.DOLLAR + Constants.OPEN_CURLY + propertyKey + Constants.CLOSE_CURLY);
                    }
                    
                    model.setProperties(properties);
                    model.setDependencies(deps);
                }
                mavenXpp3Writer.write(baos, model);
                return baos.toByteArray();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
    
    private List<License> toLicenses(String license){
        if(license!=null && !license.isEmpty()){
            License l = new License();
            l.setName(license);
            return List.of(l);
        }
        return Collections.EMPTY_LIST;
    }
    
    private Organization toOrganization(org.mvnpm.npm.model.Package p){
        Organization o = new Organization();
        if(p.author()!=null){
            o.setName(p.author().name());
        }else{
            o.setName(p.name().displayName());
        }
        if(p.homepage()!=null){
            o.setUrl(p.homepage().toString());
        }
        return o;
    }
    
    private IssueManagement toIssueManagement(Bugs bugs){
        if(bugs!=null && bugs.url()!=null){
            IssueManagement i = new IssueManagement();
            i.setUrl(bugs.url().toString());
            return i;
        }
        return null;
    }
    
    private Scm toScm(Repository repository){
        if(repository!=null && repository.url()!=null && !repository.url().isEmpty()){
            String u = repository.url();
            if(u.startsWith(GIT_PLUS)){
                u = u.substring(GIT_PLUS.length());
            }
            String conn = u;
            String repo = u;
            if(repo.endsWith(DOT_GIT)){
                repo = repo.substring(0, repo.length() - DOT_GIT.length());
            }
            if(!conn.endsWith(DOT_GIT)){
                conn = conn + DOT_GIT;
            }
            Scm s = new Scm();
            s.setUrl(repo);
            s.setConnection(conn);
            s.setDeveloperConnection(conn);
            return s;
        }
        return null;
    }
    
    private List<Developer> toDevelopers(List<Maintainer> maintainers){
        if(maintainers!=null && !maintainers.isEmpty()){
            List<Developer> ds = new ArrayList<>();
            for(Maintainer m:maintainers){
                Developer d = new Developer();
                d.setEmail(m.email());
                d.setName(m.name());
                ds.add(d);
            }
            return ds;
        }
        return Collections.EMPTY_LIST;
    }
    
    private Uni<List<Dependency>> toDependencies(Map<Name, String> dependencies){
        List<Uni<Dependency>> deps = new ArrayList<>();
        if(dependencies!=null && !dependencies.isEmpty()){
            for(Map.Entry<Name,String> e:dependencies.entrySet()){
                Name name = e.getKey();
                String version = e.getValue();
                deps.add(toDependency(name, version));
            }
        }
        
        if(!deps.isEmpty()){
            Uni<List<Dependency>> all = Uni.join().all(deps).andCollectFailures();
        
            return all.onItem().transform((a)->{
                List<Dependency> ds = new ArrayList<>();
                ds.addAll(a);
                return ds;
            });
        }else {
            return Uni.createFrom().item(List.of());
        }
        
    }
    
    private Uni<Dependency> toDependency(Name name, String version){
        Uni<String> convertedVersion = toVersion(name, version);
        return convertedVersion.onItem().transform((cv)-> {
            Dependency d = new Dependency();
            d.setGroupId(name.mvnGroupId());
            d.setArtifactId(name.mvnArtifactId());
            d.setVersion(cv);
            return d;
        });
    }
    
    private Uni<String> toVersion(Name name, String version){
        String trimVersion = VersionConverter.convert(version).trim().replaceAll("\\s+","");
        
        // This is an open ended range. Let's get the latest for a bottom boundary
        if(trimVersion.equals(OPEN_BLOCK + COMMA + CLOSE_ROUND)){
            Uni<Project> project = npmRegistryFacade.getProject(name.npmFullName());
            return project.onItem().transform((p) -> {
                return OPEN_BLOCK + p.distTags().latest() + COMMA + CLOSE_ROUND;
            });
        }
        // TODO: Make other ranges more effient too ?
        return Uni.createFrom().item(trimVersion);
    }
    
    private static final String JAR = "jar";
    
    private static final String MODEL_VERSION = "4.0.0";
    private static final String GIT_PLUS = "git+";
    private static final String DOT_GIT = ".git";
    
}

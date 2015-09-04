package com.marklogic.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jfrog.maven.annomojo.annotations.MojoGoal;

import com.marklogic.install.xquery.XQueryDocumentBuilder;
import com.marklogic.install.xquery.XQueryModule;
import com.marklogic.install.xquery.XQueryModuleAdmin;
import com.marklogic.install.xquery.XQueryModuleXDMP;
import com.marklogic.xcc.AdhocQuery;
import com.marklogic.xcc.Content;
import com.marklogic.xcc.ContentFactory;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;

/**
 * Create the necessary bootstrap configuration that the MarkLogic Plugin
 * requires for executing its goals.
 *
 * @author <a href="mailto:mark.helmstetter@marklogic.com">Mark Helmstetter</a>
 * @author <a href="mailto:bob.browning@pressassociation.com">Bob Browning</a>
 */
@MojoGoal("bootstrap")
public class BootstrapMojo extends AbstractBootstrapMojo {

    private String createDatabase() {
        XQueryDocumentBuilder xq = new XQueryDocumentBuilder();
        xq.append(XQueryModuleAdmin.importModule());
        String config = xq.assign("config", XQueryModuleAdmin.getConfiguration());
        xq.assign(config, XQueryModuleAdmin.databaseCreate(config, xdbcModulesDatabase));
        xq.doReturn(XQueryModuleAdmin.saveConfiguration(config));
        return XQueryModuleXDMP.eval(xq.toString());
    }

    private String createForest() {
        XQueryDocumentBuilder xq = new XQueryDocumentBuilder();
        xq.append(XQueryModuleAdmin.importModule());
        String config = xq.assign("config", XQueryModuleAdmin.getConfiguration());
        xq.assign(config, XQueryModuleAdmin.forestCreate(config, xdbcModulesDatabase));
        xq.doReturn(XQueryModuleAdmin.saveConfiguration(config));
        return XQueryModuleXDMP.eval(xq.toString());
    }

    private String attachForestToDatabase() {
        XQueryDocumentBuilder xq = new XQueryDocumentBuilder();
        xq.append(XQueryModuleAdmin.importModule());
        String config = xq.assign("config", XQueryModuleAdmin.getConfiguration());
        xq.assign(config, XQueryModuleAdmin.attachForest(config, getDatabase(),
                XQueryModuleXDMP.forest(xdbcModulesDatabase)));
        xq.doReturn(XQueryModuleAdmin.saveConfiguration(config));
        return XQueryModuleXDMP.eval(xq.toString());
    }

    private String createWebDAVServer() {
        XQueryDocumentBuilder xq = new XQueryDocumentBuilder();
        xq.append(XQueryModuleAdmin.importModule());
        String config = xq.assign("config", XQueryModuleAdmin.getConfiguration());
        xq.assign(config, XQueryModuleAdmin.webdavServerCreate(config, xdbcName + "-WebDAV", xdbcModuleRoot,
                xdbcPort + 1, getDatabase()));
        xq.doReturn(XQueryModuleAdmin.saveConfiguration(config));
        return XQueryModuleXDMP.eval(xq.toString());
    }

    private String createXDBCServer() {
        XQueryDocumentBuilder xq = new XQueryDocumentBuilder();
        xq.append(XQueryModuleAdmin.importModule());
        String config = xq.assign("config", XQueryModuleAdmin.getConfiguration());
        xq.assign(config, XQueryModuleAdmin.xdbcServerCreate(config, xdbcName, xdbcModuleRoot, xdbcPort,
                getDatabase(), XQueryModuleXDMP.database("Security")));
        xq.doReturn(XQueryModuleAdmin.saveConfiguration(config));
        return XQueryModuleXDMP.eval(xq.toString());
    }

    private String getDatabase() {
        return xdbcModulesDatabase.equalsIgnoreCase(FILE_SYSTEM) ? "'" + FILE_SYSTEM + "'" : XQueryModuleXDMP.database(xdbcModulesDatabase);
    }

    protected String getBootstrapExecuteQuery() {
        XQueryDocumentBuilder sb = new XQueryDocumentBuilder();
        if (!FILE_SYSTEM.equalsIgnoreCase(xdbcModulesDatabase)) {
            sb.assign("_", createDatabase());
            sb.assign("_", createForest());
            sb.assign("_", attachForestToDatabase());
        }
        sb.assign("_", createXDBCServer());

        sb.doReturn(XQueryModule.quote("Bootstrap Install - OK"));

        /* Log xquery invocation */
        getLog().debug(sb.toString());

        return sb.toString();
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
    	if (!installBootstrap) {
    		return;    
    	} 
    	
        Session session = getXccSession();        
        getLog().info("Bootstrap session is to " + session.getConnectionUri().toASCIIString());
        
		AdhocQuery q = session.newAdhocQuery("xquery version \"1.0-ml\";\n1");
		boolean success = false;
		try {
			session.submitRequest(q);
			success = true;
		} catch (RequestException e) {
		} finally {
			session.close();
		}
        
        if (success) {
        	getLog().info("Bootstrap already exists, skipping this goal");
        	return;
        }
               
        super.execute();

        if (!FILE_SYSTEM.equalsIgnoreCase(xdbcModulesDatabase)) {
            this.database = xdbcModulesDatabase;
            session = getXccSession();        
            session.setTransactionMode(Session.TransactionMode.UPDATE);
            try {
                String[] paths = {"/install.xqy"
                        , "/lib/lib-app-server.xqy"
                        , "/lib/lib-cpf.xqy"
                        , "/lib/lib-database-add.xqy"
                        , "/lib/lib-database-set.xqy"
                        , "/lib/lib-database.xqy"
                        , "/lib/lib-field.xqy"
                        , "/lib/lib-trigger.xqy"
                        , "/lib/lib-task.xqy"
                        , "/lib/lib-index.xqy"
                        , "/lib/lib-install.xqy"
                        , "/lib/lib-load.xqy"};

                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                for (String path : paths) {
                    getLog().info("Uploading " + path);
                    try {
                        Content cs = ContentFactory.newContent(path, loader.getResource("xquery" + path), null);
                        session.insertContent(cs);
                    } catch (Exception e) {
                        getLog().error("Failed to insert required library.");
                    } finally {
                    	getLog().info("committing to db");
                    	session.commit();
                    }
                }
            } catch (RequestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
                session.close();
            }
        } else {
            getLog().warn("");
            getLog().warn("***************************************************************");
            getLog().warn("* Using filesystem modules location, ensure that install.xqy  *");
            getLog().warn("* and associated libraries are placed into the specified root *");
            getLog().warn("***************************************************************");
            getLog().warn("");
        }
    }
}

package br.ufrj.ppgi.greco.kettle;

import java.io.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.repository.http.config.HTTPRepositoryConfig;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryProvider;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.RDFParseException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;


/**
 * Step Load Triple File.
 * 
 * Carrega um arquivo RDF em um banco de dados em grafos (GraphDB)
 * 
 * 
 * @author Nickolas Gomes Pinto
 * 
 */
public class LoadTripleFileStep extends BaseStep implements StepInterface {


    // Construtor
	public LoadTripleFileStep(StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
			Trans trans) {
		super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
	}

	@Override
	public boolean init(StepMetaInterface smi, StepDataInterface sdi) {

		if (super.init(smi, sdi)) {
			return true;
		} else
			return false;

	} 

	@Override
	public void dispose(StepMetaInterface smi, StepDataInterface sdi) {
		super.dispose(smi, sdi);
	}

	/**
	 * Metodo chamado para cada linha que entra no step
	 */
	public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException {
		LoadTripleFileStepMeta meta = (LoadTripleFileStepMeta) smi;
		LoadTripleFileStepData data = (LoadTripleFileStepData) sdi;

		// Obtem linha do fluxo de entrada e termina caso nao haja mais entrada

		Object[] row = getRow();

		if (row == null) { // Nao ha mais linhas de dados

			String inputFileFormat = meta.getInputFileFormat();
			String inputExistsRepository = meta.getExistsRepository();
			String inputRepoName = meta.getInputRepoName();
			String inputGraphName = meta.getInputGraph();
			String inputRepoURL = meta.getInputRepoURL();
			String browseFilename = meta.getBrowseFilename();

			try{

				// Se conecta ao Branco de Grafo
				RepositoryManager manager = InitRepo(inputRepoURL);

				// Obtendo e se conectando ao repositorio especifico do banco
				String repo_name = CreateRepo(manager, inputExistsRepository, inputRepoName);
				Repository repository = manager.getRepository(repo_name);
				RepositoryConnection repoCon = ConnectRepo(repository);

				/////////////////////////// Recupera arquivo
				File file = RetrieveFile(browseFilename);

				/////////////////////////// Cria Grafo nomeado
				IRI context = ConnectRepoAndCreateNamedGraph(repoCon, inputGraphName);

				/////////////////////////// Carrega arquivo
				uploadFile(repoCon, file, context, inputFileFormat);

				//Encerrar a conexão
				repoCon.close();
				repository.shutDown();
				manager.shutDown();

			} catch (IOException e) {
				e.printStackTrace();
            }			

			setOutputDone();
			return false;
		}

		return true;
	}

	// Inicializa o Repositório
	public static RepositoryManager InitRepo(String repo_path) {

        String repo_url = repo_path;
        RepositoryManager manager = RepositoryProvider.getRepositoryManager(repo_url);
        manager.init();
        manager.getAllRepositories();
        
        return manager;

    }

	// Cria repositório
	public static String CreateRepo(RepositoryManager manager, String inputExistsRepository, String inputRepoName) throws IOException {


        // Verifica se vai usar repositorio ja existente ou criar um novo
        inputExistsRepository = inputExistsRepository.toUpperCase();

		// Cria variavel auxiliar para repo_name
		String repo_name = null;
        
        // Repositorio ja existe
        if ( inputExistsRepository.equals("S") ){

            repo_name = inputRepoName;
				
        } else if( inputExistsRepository.equals("N") ){ // Repositorio nao existe, cria um default

            InputStream config_file = null;
            RDFParser rdfParser_configFile = null;
            TreeModel graph_Node = new TreeModel();

            try {

				config_file = new FileInputStream(new File(System.getProperty("user.dir") + "\\plugins\\steps\\LoadTripleFile\\lib\\repo-defaults_test.ttl"));


            } catch (FileNotFoundException e) {				
                e.printStackTrace();
            }

            rdfParser_configFile = Rio.createParser(RDFFormat.TURTLE);
           	rdfParser_configFile.setRDFHandler(new StatementCollector(graph_Node));


			try {

                rdfParser_configFile.parse(config_file, RepositoryConfigSchema.NAMESPACE);

            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {

                config_file.close(); }

            //Obtendo o repositório como recurso
            Resource repoNode_test = Models.subject(graph_Node.filter(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY)).orElseThrow(() -> new RuntimeException("Oops, no <http://www.openrdf.org/config/repository#> subject found!"));

            //Adicionando as configurações
            RepositoryConfig configObj = RepositoryConfig.create(graph_Node, repoNode_test);
            manager.addRepositoryConfig(configObj);

            repo_name = "repo_pdi";

        }

		return repo_name;

    }

	public static File RetrieveFile(String browseFilename){

        // Recupera arquivo e cria objeto File
		File file = new File(browseFilename); 

        return file;

    }

	public static RepositoryConnection ConnectRepo(Repository repository){

        //Conectar ao repositorio
        RepositoryConnection repoCon = repository.getConnection();

        return repoCon;

    }

	public static IRI ConnectRepoAndCreateNamedGraph(RepositoryConnection repoConnection, String inputGraphName){
			
        String ex = "http://etl4fair.com/";
        ValueFactory vf = repoConnection.getValueFactory();
        IRI context = vf.createIRI(ex, inputGraphName); // Cria contexto para novo grafo nomeado

        return context;

    }

	public static void uploadFile(RepositoryConnection repoConnection, File file_Path, IRI context, String inputFileFormat){


        // Base URI
        String baseURI = "http://example.org/example/local";

        switch (inputFileFormat){

            // Arquivos .ttl
            case "TURTLE":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.TURTLE, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .rdf, .rdfs, .owl, .xml
            case "RDFXML":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.RDFXML, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .rj
            case "RDFJSON":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.RDFJSON, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .n3
            case "N3":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.N3, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .nt
            case "NTRIPLES":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.NTRIPLES, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .nq
            case "NQUAD":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.NQUADS, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .trig
            case "TRIG":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.TRIG, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .trix
            case "TRIX":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.TRIX, context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

            // Arquivos .jsonld
            case "JSONLD":
                try {

                    repoConnection.add(file_Path, baseURI, RDFFormat.JSONLD,context);

                } catch (IOException e) {

                    System.out.println("\\n");
                    System.out.println("Formato de arquivo errado, por favor, informe o formato correto.");
                    e.printStackTrace();

                }
                break;

        }

    }
}

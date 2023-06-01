package eu.glasskube.operator.apps.odoo.dependent

import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.kubernetes.api.model.secretKeySelector
import eu.glasskube.operator.apps.odoo.Odoo
import eu.glasskube.operator.apps.odoo.OdooReconciler
import eu.glasskube.operator.apps.odoo.bucketName
import eu.glasskube.operator.apps.odoo.dbBackupSecretName
import eu.glasskube.operator.apps.odoo.dbName
import eu.glasskube.operator.apps.odoo.resourceLabels
import eu.glasskube.operator.config.ConfigKey
import eu.glasskube.operator.config.ConfigService
import eu.glasskube.operator.infra.postgres.BackupConfiguration
import eu.glasskube.operator.infra.postgres.BarmanObjectStoreConfiguration
import eu.glasskube.operator.infra.postgres.BootstrapConfiguration
import eu.glasskube.operator.infra.postgres.BootstrapInitDB
import eu.glasskube.operator.infra.postgres.ClusterSpec
import eu.glasskube.operator.infra.postgres.CompressionType
import eu.glasskube.operator.infra.postgres.DataBackupConfiguration
import eu.glasskube.operator.infra.postgres.MonitoringConfiguration
import eu.glasskube.operator.infra.postgres.PostgresCluster
import eu.glasskube.operator.infra.postgres.S3Credentials
import eu.glasskube.operator.infra.postgres.StorageConfiguration
import eu.glasskube.operator.infra.postgres.WalBackupConfiguration
import eu.glasskube.operator.infra.postgres.postgresCluster
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent

@KubernetesDependent(labelSelector = OdooReconciler.SELECTOR)
class OdooPostgresCluster(private val configService: ConfigService) :
    CRUDKubernetesDependentResource<PostgresCluster, Odoo>(PostgresCluster::class.java) {
    override fun desired(primary: Odoo, context: Context<Odoo>) = postgresCluster {
        metadata {
            name = primary.dbName
            namespace = primary.metadata.namespace
            labels = primary.resourceLabels
        }
        spec = ClusterSpec(
            instances = 1,
            enableSuperuserAccess = false,
            bootstrap = BootstrapConfiguration(
                initdb = BootstrapInitDB(database = Odoo.dbName)
            ),
            storage = StorageConfiguration(
                storageClass = configService[ConfigKey.databaseStorageClassName],
                size = "10Gi"
            ),
            backup = BackupConfiguration(
                BarmanObjectStoreConfiguration(
                    endpointURL = "http://glasskube-minio.glasskube-system:9000",
                    destinationPath = "s3://${primary.bucketName}",
                    s3Credentials = S3Credentials(
                        accessKeyId = secretKeySelector(primary.dbBackupSecretName, "username"),
                        secretAccessKey = secretKeySelector(primary.dbBackupSecretName, "password")
                    ),
                    wal = WalBackupConfiguration(
                        compression = CompressionType.GZIP
                    ),
                    data = DataBackupConfiguration(
                        compression = CompressionType.GZIP
                    )
                ),
                retentionPolicy = "15d"
            ),
            monitoring = MonitoringConfiguration(
                enablePodMonitor = true
            )
        )
    }
}
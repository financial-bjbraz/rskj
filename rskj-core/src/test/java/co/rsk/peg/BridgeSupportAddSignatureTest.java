package co.rsk.peg;

import java.time.Instant;
import java.math.BigInteger;
import java.util.*;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.core.RskAddress;
import co.rsk.peg.utils.*;
import co.rsk.test.builders.BridgeSupportBuilder;

import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static co.rsk.peg.PegTestUtils.*;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Mockito.*;

class BridgeSupportAddSignatureTest extends BridgeSupportTestBase {

    protected ActivationConfig.ForBlock activationsBeforeForks;
    protected ActivationConfig.ForBlock activationsAfterForks;
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    @BeforeEach
    void setUpOnEachTest() {
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    @Test
    void addSignature_fedPubKey_belongs_to_active_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);

        // Creates new federation
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcRegTestParams
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstantsRegtest,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                new Context(bridgeConstantsRegtest.getBtcParams()),
                mockFederationSupport,
                null,
                null,
                null
        );

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa01")), null,
                createHash3(1).getBytes());

        verify(provider, times(1)).getRskTxsWaitingForSignatures();
    }

    @Test
    void addSignature_fedPubKey_belongs_to_retiring_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstantsRegtest,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                new Context(bridgeConstantsRegtest.getBtcParams()),
                mockFederationSupport,
                null,
                null,
                null
        );

        // Creates retiring federation
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")));
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcRegTestParams
        );

        // Creates active federation
        List<BtcECKey> activeFederationKeys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa03")),
                BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        activeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcRegTestParams
        );

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa01")), null,
                createHash3(1).getBytes());

        verify(provider, times(1)).getRskTxsWaitingForSignatures();
    }

    @Test
    void addSignature_fedPubKey_no_belong_to_retiring_or_active_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstantsRegtest,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                new Context(bridgeConstantsRegtest.getBtcParams()),
                mockFederationSupport,
                null,
                null,
                null
        );

        // Creates retiring federation
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcRegTestParams
        );

        // Creates active federation
        List<BtcECKey> activeFederationKeys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa03")),
                BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        activeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcRegTestParams);

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa05")), null,
                createHash3(1).getBytes());

        verify(provider, times(0)).getRskTxsWaitingForSignatures();
    }

    @Test
    void addSignature_fedPubKey_no_belong_to_active_federation_no_existing_retiring_fed() throws Exception {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstantsRegtest)
                .withProvider(provider)
                .withEventLogger(mock(BridgeEventLogger.class))
                .build();

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa03")), null,
                createHash3(1).getBytes());

        verify(provider, times(0)).getRskTxsWaitingForSignatures();
    }

    @Test
    void addSignatureToMissingTransaction() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Repository repository = createRepository();

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstantsRegtest,
                activationsBeforeForks
        );

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstantsRegtest)
                .withProvider(providerForSupport)
                .withRepository(repository)
                .build();

        bridgeSupport.addSignature(federation.getBtcPublicKeys().get(0), null, createHash().getBytes());
        bridgeSupport.save();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstantsRegtest, activationsBeforeForks);

        assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    void addSignatureFromInvalidFederator() throws Exception {

        Repository repository = createRepository();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstantsRegtest)
                .withProvider(new BridgeStorageProvider(
                        repository,
                        PrecompiledContracts.BRIDGE_ADDR,
                        bridgeConstantsRegtest,
                        activationsBeforeForks))
                .withRepository(repository)
                .build();

        bridgeSupport.addSignature(new BtcECKey(), null, createHash().getBytes());
        bridgeSupport.save();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstantsRegtest, activationsBeforeForks);

        assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    void addSignatureWithInvalidSignature() throws Exception {
        List<BtcECKey> list = new ArrayList<>();
        list.add(new BtcECKey());
        addSignatureFromValidFederator(list, 1, true, false, "InvalidParameters");
    }

    @Test
    void addSignatureWithLessSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 0, true, false, "InvalidParameters");
    }

    @Test
    void addSignatureWithMoreSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 2, true, false, "InvalidParameters");
    }

    @Test
    void addSignatureNonCanonicalSignature() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, false, false, "InvalidParameters");
    }

    private void test_addSignature_EventEmitted(boolean rskip326Active, boolean useValidSignature, int wantedNumberOfInvocations, boolean shouldSignTwice) throws Exception {
        // Setup
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Repository track = createRepository().startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activationsBeforeForks);

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut = new TransactionOutput(btcRegTestParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        TransactionOutput output = new TransactionOutput(btcRegTestParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        btcTx.addOutput(output);

        // Save btc tx to be signed
        final Keccak256 rskTxHash = createHash3(1);
        provider.getRskTxsWaitingForSignatures().put(rskTxHash, btcTx);
        provider.save();
        track.commit();

        // Setup BridgeSupport
        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(rskip326Active);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstantsRegtest)
                .withProvider(provider)
                .withRepository(track)
                .withEventLogger(eventLogger)
                .withBtcLockSenderProvider(new BtcLockSenderProvider())
                .withBtcBlockStoreFactory(null)
                .withActivations(activations)
                .build();

        int indexOfKeyToSignWith = 0;

        BtcECKey privateKeyToSignWith = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(indexOfKeyToSignWith);

        List derEncodedSigs;

        if(useValidSignature) {
            Script inputScript = btcTx.getInputs().get(0).getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sigHash = btcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
            BtcECKey.ECDSASignature sig = privateKeyToSignWith.sign(sigHash);
            derEncodedSigs = Collections.singletonList(sig.encodeToDER());
        } else {
            byte[] malformedSignature = new byte[70];
            for (int i = 0; i < malformedSignature.length; i++) {
                malformedSignature[i] = (byte) i;
            }
            derEncodedSigs = Collections.singletonList(malformedSignature);
        }

        BtcECKey federatorPubKey = BridgeRegTestConstants.REGTEST_FEDERATION_PUBLIC_KEYS.get(indexOfKeyToSignWith);
        bridgeSupport.addSignature(federatorPubKey, derEncodedSigs, rskTxHash.getBytes());
        if(shouldSignTwice) {
            bridgeSupport.addSignature(federatorPubKey, derEncodedSigs, rskTxHash.getBytes());
        }

        verify(eventLogger, times(wantedNumberOfInvocations)).logAddSignature(federatorPubKey, btcTx, rskTxHash.getBytes());
    }

    @Test
    void addSignature_calledTwiceWithSameFederatorPreRSKIP326_emitEventTwice() throws Exception {
        test_addSignature_EventEmitted(false, true, 2, true);
    }

    @Test
    void addSignature_calledTwiceWithSameFederatorPostRSKIP326_emitEventOnlyOnce() throws Exception {
        test_addSignature_EventEmitted(true, true, 1, true);
    }

    @Test
    void addSignatureCreateEventLog_preRSKIP326() throws Exception {
        test_addSignature_EventEmitted(false, true, 1, false);
    }

    @Test
    void addSignature_invalidSignatureBeforeRSKIP326_eventStillEmitted() throws Exception {
        test_addSignature_EventEmitted(false, false, 1, false);
    }

    @Test
    void addSignature_afterRSKIP326_eventEmitted() throws Exception {
        test_addSignature_EventEmitted(true, true, 1, false);
    }

    @Test
    void addSignature_invalidSignatureAfterRSKIP326_noEventEmitted() throws Exception {
        test_addSignature_EventEmitted(true, false, 0, false);
    }

    @Test
    void addSignatureTwice() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, true, true, "PartiallySigned");
    }

    @Test
    void addSignatureOneSignature() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, true, false, "PartiallySigned");
    }

    @Test
    void addSignatureTwoSignatures() throws Exception {
        List<BtcECKey> federatorPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        List<BtcECKey> keys = Arrays.asList(federatorPrivateKeys.get(0), federatorPrivateKeys.get(1));
        addSignatureFromValidFederator(keys, 1, true, false, "FullySigned");
    }

    @Test
    void addSignatureMultipleInputsPartiallyValid() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Repository repository = createRepository();

        final Keccak256 keccak256 = createHash3(1);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstantsRegtest, activationsBeforeForks);

        BtcTransaction prevTx1 = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut1 = new TransactionOutput(btcRegTestParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx1.addOutput(prevOut1);
        BtcTransaction prevTx2 = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut2 = new TransactionOutput(btcRegTestParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx2.addOutput(prevOut2);
        BtcTransaction prevTx3 = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut3 = new TransactionOutput(btcRegTestParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx3.addOutput(prevOut3);

        BtcTransaction t = new BtcTransaction(btcRegTestParams);
        TransactionOutput output = new TransactionOutput(btcRegTestParams, t, Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        t.addOutput(output);
        t.addInput(prevOut1).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        t.addInput(prevOut2).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        t.addInput(prevOut3).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(keccak256, t);
        provider.save();

        SignatureCache signatureCache = new ReceivedTxSignatureCache();

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BrigeEventLoggerLegacyImpl(bridgeConstantsRegtest, activations, logs, signatureCache);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstantsRegtest)
                .withProvider(provider)
                .withRepository(repository)
                .withEventLogger(eventLogger)
                .build();

        // Generate valid signatures for inputs
        List<byte[]> derEncodedSigsFirstFed = new ArrayList<>();
        List<byte[]> derEncodedSigsSecondFed = new ArrayList<>();
        BtcECKey privateKeyOfFirstFed = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0);
        BtcECKey privateKeyOfSecondFed = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(1);

        BtcECKey.ECDSASignature lastSig = null;
        for (int i = 0; i < 3; i++) {
            Script inputScript = t.getInput(i).getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = t.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false);

            // Sign the last input with a random key
            // but keep the good signature for a subsequent call
            BtcECKey.ECDSASignature sig = privateKeyOfFirstFed.sign(sighash);
            if (i == 2) {
                lastSig = sig;
                sig = new BtcECKey().sign(sighash);
            }
            derEncodedSigsFirstFed.add(sig.encodeToDER());
            derEncodedSigsSecondFed.add(privateKeyOfSecondFed.sign(sighash).encodeToDER());
        }

        // Sign with two valid signatures and one invalid signature
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();

        // Sign with two valid signatures and one malformed signature
        byte[] malformedSignature = new byte[lastSig.encodeToDER().length];
        for (int i = 0; i < malformedSignature.length; i++) {
            malformedSignature[i] = (byte) i;
        }
        derEncodedSigsFirstFed.set(2, malformedSignature);
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();

        // Sign with fully valid signatures for same federator
        derEncodedSigsFirstFed.set(2, lastSig.encodeToDER());
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();

        // Sign with second federation
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfSecondFed), derEncodedSigsSecondFed, keccak256.getBytes());
        bridgeSupport.save();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activationsBeforeForks);

        assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        assertThat(logs, is(not(empty())));
        assertThat(logs, hasSize(5));
        LogInfo releaseTxEvent = logs.get(4);
        assertThat(releaseTxEvent.getTopics(), hasSize(1));
        assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
        BtcTransaction releaseTx = new BtcTransaction(btcRegTestParams, ((RLPList) RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
        // Verify all inputs fully signed
        for (int i = 0; i < releaseTx.getInputs().size(); i++) {
            Script retrievedScriptSig = releaseTx.getInput(i).getScriptSig();
            assertEquals(4, retrievedScriptSig.getChunks().size());
            assertTrue(Objects.requireNonNull(retrievedScriptSig.getChunks().get(1).data).length > 0);
            assertTrue(Objects.requireNonNull(retrievedScriptSig.getChunks().get(2).data).length > 0);
        }
    }

    /**
     * Helper method to test addSignature() with a valid federatorPublicKey parameter and both valid/invalid signatures
     *
     * @param privateKeysToSignWith keys used to sign the tx. Federator key when we want to produce a valid signature, a random key when we want to produce an invalid signature
     * @param numberOfInputsToSign  There is just 1 input. 1 when testing the happy case, other values to test attacks/bugs.
     * @param signatureCanonical    Signature should be canonical. true when testing the happy case, false to test attacks/bugs.
     * @param signTwice             Sign again with the same key
     * @param expectedResult        "InvalidParameters", "PartiallySigned" or "FullySigned"
     */
    private void addSignatureFromValidFederator(List<BtcECKey> privateKeysToSignWith, int numberOfInputsToSign, boolean signatureCanonical, boolean signTwice, String expectedResult) throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Repository repository = createRepository();

        final Keccak256 keccak256 = PegTestUtils.createHash3();

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activationsBeforeForks);

        BtcTransaction prevTx = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut = new TransactionOutput(btcRegTestParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        BtcTransaction t = new BtcTransaction(btcRegTestParams);
        TransactionOutput output = new TransactionOutput(btcRegTestParams, t, Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        t.addOutput(output);
        t.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(keccak256, t);
        provider.save();
        track.commit();

        SignatureCache signatureCache = new ReceivedTxSignatureCache();

        track = repository.startTracking();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BrigeEventLoggerLegacyImpl(bridgeConstantsRegtest, activations, logs, signatureCache);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstantsRegtest)
                .withProvider(new BridgeStorageProvider(
                        track,
                        contractAddress,
                        bridgeConstantsRegtest,
                        activationsAfterForks
                ))
                .withRepository(track)
                .withEventLogger(eventLogger)
                .build();

        Script inputScript = t.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sighash = t.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature sig = privateKeysToSignWith.get(0).sign(sighash);
        if (!signatureCanonical) {
            sig = new BtcECKey.ECDSASignature(sig.r, BtcECKey.CURVE.getN().subtract(sig.s));
        }
        byte[] derEncodedSig = sig.encodeToDER();

        List derEncodedSigs = new ArrayList();
        for (int i = 0; i < numberOfInputsToSign; i++) {
            derEncodedSigs.add(derEncodedSig);
        }
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeysToSignWith.get(0)), derEncodedSigs, keccak256.getBytes());
        if (signTwice) {
            // Create another valid signature with the same private key
            ECDSASigner signer = new ECDSASigner();
            X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
            ECDomainParameters CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
            ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKeysToSignWith.get(0).getPrivKey(), CURVE);
            signer.init(true, privKey);
            BigInteger[] components = signer.generateSignature(sighash.getBytes());
            BtcECKey.ECDSASignature sig2 = new BtcECKey.ECDSASignature(components[0], components[1]).toCanonicalised();
            List<byte[]> list = new ArrayList<>();
            list.add(sig2.encodeToDER());
            bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeysToSignWith.get(0)), list, keccak256.getBytes());
        }
        if (privateKeysToSignWith.size() > 1) {
            BtcECKey.ECDSASignature sig2 = privateKeysToSignWith.get(1).sign(sighash);
            byte[] derEncodedSig2 = sig2.encodeToDER();
            List derEncodedSigs2 = new ArrayList();
            for (int i = 0; i < numberOfInputsToSign; i++) {
                derEncodedSigs2.add(derEncodedSig2);
            }
            bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeysToSignWith.get(1)), derEncodedSigs2, keccak256.getBytes());
        }
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activationsBeforeForks);

        if ("FullySigned".equals(expectedResult)) {
            assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
            assertThat(logs, is(not(empty())));
            assertThat(logs, hasSize(3));
            LogInfo releaseTxEvent = logs.get(2);
            assertThat(releaseTxEvent.getTopics(), hasSize(1));
            assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
            BtcTransaction releaseTx = new BtcTransaction(btcRegTestParams, ((RLPList) RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
            Script retrievedScriptSig = releaseTx.getInput(0).getScriptSig();
            assertEquals(4, retrievedScriptSig.getChunks().size());
            assertTrue(retrievedScriptSig.getChunks().get(1).data.length > 0);
            assertTrue(retrievedScriptSig.getChunks().get(2).data.length > 0);
        } else {
            Script retrievedScriptSig = provider.getRskTxsWaitingForSignatures().get(keccak256).getInput(0).getScriptSig();
            assertEquals(4, retrievedScriptSig.getChunks().size());
            boolean expectSignatureToBePersisted = "PartiallySigned".equals(expectedResult); // for "InvalidParameters"
            assertEquals(expectSignatureToBePersisted, retrievedScriptSig.getChunks().get(1).data.length > 0);
            assertFalse(retrievedScriptSig.getChunks().get(2).data.length > 0);
        }
    }

    private BtcECKey findPublicKeySignedBy(List<BtcECKey> pubs, BtcECKey pk) {
        for (BtcECKey pub : pubs) {
            if (Arrays.equals(pk.getPubKey(), pub.getPubKey())) {
                return pub;
            }
        }
        return pk;
    }

}

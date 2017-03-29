package com.etesync.syncadapter.journalmanager;

import com.google.gson.reflect.TypeToken;

import org.spongycastle.util.Arrays;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.GsonHelper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.etesync.syncadapter.journalmanager.Crypto.sha256;
import static com.etesync.syncadapter.journalmanager.Crypto.toHex;

public class JournalManager extends BaseManager {
    final static private Type journalType = new TypeToken<List<Journal>>() {
    }.getType();


    public JournalManager(OkHttpClient httpClient, HttpUrl remote) {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/journals")
                .addPathSegment("")
                .build();
        App.log.info("Created for: " + this.remote.toString());

        this.client = httpClient;
    }

    public List<Journal> getJournals(String keyBase64) throws Exceptions.HttpException, Exceptions.IntegrityException {
        Request request = new Request.Builder()
                .get()
                .url(remote)
                .build();

        Response response = newCall(request);
        ResponseBody body = response.body();
        List<Journal> ret = GsonHelper.gson.fromJson(body.charStream(), journalType);

        for (Journal journal : ret) {
            journal.processFromJson();
            journal.verify(keyBase64);
        }

        return ret;
    }

    public void deleteJournal(Journal journal) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.resolve(journal.getUuid() + "/");
        Request request = new Request.Builder()
                .delete()
                .url(remote)
                .build();

        newCall(request);
    }

    public void putJournal(Journal journal) throws Exceptions.HttpException {
        RequestBody body = RequestBody.create(JSON, journal.toJson());

        Request request = new Request.Builder()
                .post(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public void updateJournal(Journal journal) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.resolve(journal.getUuid() + "/");
        RequestBody body = RequestBody.create(JSON, journal.toJson());

        Request request = new Request.Builder()
                .put(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public static class Journal extends Base {
        final private transient int hmacSize = 256 / 8; // hmac256 in bytes
        private transient byte[] hmac = null;

        @SuppressWarnings("unused")
        private Journal() {
            super();
        }

        public Journal(String keyBase64, String content) {
            this(keyBase64, content, sha256(UUID.randomUUID().toString()));
        }

        public Journal(String keyBase64, String content, String uid) {
            super(keyBase64, content, uid);
            hmac = calculateHmac(keyBase64);
        }

        private void processFromJson() {
            hmac = Arrays.copyOfRange(getContent(), 0, hmacSize);
            setContent(Arrays.copyOfRange(getContent(), hmacSize, getContent().length));
        }

        void verify(String keyBase64) throws Exceptions.IntegrityException {
            if (hmac == null) {
                throw new Exceptions.IntegrityException("HMAC is null!");
            }

            byte[] correctHash = calculateHmac(keyBase64);
            if (!Arrays.areEqual(hmac, correctHash)) {
                throw new Exceptions.IntegrityException("Bad HMAC. " + toHex(hmac) + " != " + toHex(correctHash));
            }
        }

        byte[] calculateHmac(String keyBase64) {
            return super.calculateHmac(keyBase64, getUuid());
        }

        @Override
        String toJson() {
            byte[] rawContent = getContent();
            setContent(Arrays.concatenate(hmac, rawContent));
            String ret = super.toJson();
            setContent(rawContent);
            return ret;
        }
    }
}

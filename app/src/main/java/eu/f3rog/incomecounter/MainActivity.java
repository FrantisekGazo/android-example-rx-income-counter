package eu.f3rog.incomecounter;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import blade.Blade;
import blade.State;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.internal.disposables.ListCompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Blade
public final class MainActivity
        extends BaseActivity {

    public static final double DEFAULT_PRICE = 15d;

    @State
    long mStartTime;

    @NonNull
    private ListCompositeDisposable mDisposables = new ListCompositeDisposable();

    @NonNull
    private EditText mHourPriceText;
    @NonNull
    private TextView mIncomeText;
    @NonNull
    private TextView mTimeText;

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            mStartTime = System.currentTimeMillis();
        }

        mHourPriceText = (EditText) findViewById(R.id.price);
        mIncomeText = (TextView) findViewById(R.id.income);
        mTimeText = (TextView) findViewById(R.id.time);

        mHourPriceText.setText(String.format(Locale.getDefault(), "%.2f", DEFAULT_PRICE));

        final Disposable disposable = Flowable.combineLatest(hoursElapsed(), currentHourlyPrice(),
                new BiFunction<Double, Double, Double>() {
                    @Override
                    public Double apply(@NonNull final Double hours, @NonNull final Double price) {
                        return hours * price;
                    }
                })
                .subscribeOn(Schedulers.io())
                .map(new Function<Double, String>() {
                    @Override
                    public String apply(@NonNull final Double income) {
                        return String.format(Locale.getDefault(), "%.3f€", income);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(@NonNull final String text) throws Exception {
                        mIncomeText.setText(text);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull final Throwable e) throws Exception {
                        mIncomeText.setText("Error: " + e.getMessage());
                    }
                });

        mDisposables.add(disposable);
    }

    private Flowable<Double> hoursElapsed() {
        return Flowable.interval(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .map(new Function<Long, Long>() {
                    @Override
                    public Long apply(@NonNull final Long ignored) {
                        final long currentTime = System.currentTimeMillis();
                        return currentTime - mStartTime;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(@NonNull final Long ms) {
                        mTimeText.setText(formatInterval(ms));
                    }
                })
                .observeOn(Schedulers.io())
                .map(new Function<Long, Double>() {
                    @Override
                    public Double apply(@NonNull final Long ms) {
                        return (double) ms / 1000d / 60d / 60d;
                    }
                });
    }

    private static String formatInterval(final long ms) {
        final long hr = TimeUnit.MILLISECONDS.toHours(ms);
        final long min = TimeUnit.MILLISECONDS.toMinutes(ms - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(ms - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
        final long restMs = TimeUnit.MILLISECONDS.toMillis(ms - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
        return String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hr, min, sec, restMs);
    }

    private Flowable<Double> currentHourlyPrice() {
        return Flowable.create(new FlowableOnSubscribe<Double>() {
            @Override
            public void subscribe(@NonNull final FlowableEmitter<Double> emitter) throws Exception {
                emitter.onNext(getHourPrice());
                mHourPriceText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        emitter.onNext(getHourPrice());
                    }
                });
            }
        }, BackpressureStrategy.BUFFER);
    }

    private Double getHourPrice() {
        final String text = mHourPriceText.getText().toString();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!mDisposables.isDisposed()) {
            mDisposables.dispose();
        }
    }
}
